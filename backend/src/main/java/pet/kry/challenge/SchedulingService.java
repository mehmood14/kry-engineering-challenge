package pet.kry.challenge;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class SchedulingService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Stockholm");
    private static final List<LocalTime> STARTS = List.of(
        LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30));
    private final Clock clock;
    private final List<Booking> bookings = new ArrayList<>();

    public SchedulingService() { this(Clock.system(CLINIC_ZONE)); }
    SchedulingService(Clock clock) { this.clock = clock; }

    public synchronized List<OffsetDateTime> availableSlots(Recommendation recommendation) {
        Objects.requireNonNull(recommendation, "recommendation");
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(CLINIC_ZONE);
        List<OffsetDateTime> result = new ArrayList<>();
        for (int day = 0; day <= 3; day++) {
            LocalDate date = now.toLocalDate().plusDays(day);
            for (LocalTime time = LocalTime.of(8, 0); time.isBefore(LocalTime.of(18, 0)); time = time.plusMinutes(15)) {
                ZonedDateTime start = ZonedDateTime.of(date, time, CLINIC_ZONE);
                if (start.isBefore(now)) continue;
                ZonedDateTime end = start.plusMinutes(recommendation.getDurationMinutes());
                if (hasAvailableClinician(start, end)) result.add(start.toOffsetDateTime());
            }
        }
        return result;
    }

    public synchronized List<OffsetDateTime> availableSlots() {
        return availableSlots(Recommendation.Chat);
    }

    private boolean hasAvailableClinician(ZonedDateTime appointmentStart, ZonedDateTime appointmentEnd) {
        for (LocalTime clinicianStart : STARTS) {
            if (coversWorkPeriod(clinicianStart, appointmentStart.toLocalTime(), appointmentEnd.toLocalTime()) &&
                bookings.stream().noneMatch(booking ->
                    booking.clinicianStart().equals(clinicianStart) &&
                    overlaps(appointmentStart.toInstant(), appointmentEnd.toInstant(),
                        booking.start(), booking.end()))) {
                return true;
            }
        }
        return false;
    }

    static boolean coversWorkPeriod(LocalTime clinicianStart, LocalTime appointmentStart, LocalTime appointmentEnd) {
        LocalTime breakStart = clinicianStart.plusHours(4);
        LocalTime breakEnd = breakStart.plusHours(1);
        LocalTime finish = clinicianStart.plusHours(9);
        return (!appointmentStart.isBefore(clinicianStart) && !appointmentEnd.isAfter(breakStart)) ||
            (!appointmentStart.isBefore(breakEnd) && !appointmentEnd.isAfter(finish));
    }

    private static boolean overlaps(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    public synchronized String book(OffsetDateTime slot, Recommendation recommendation) {
        if (slot == null) {
            throw new IllegalArgumentException("Appointment slot is required");
        }
        if (recommendation == null) {
            throw new IllegalArgumentException("Recommendation is required");
        }

        ZonedDateTime start = slot.atZoneSameInstant(CLINIC_ZONE);
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(CLINIC_ZONE);
        LocalDate firstDate = now.toLocalDate();
        LocalDate lastDate = firstDate.plusDays(3);
        if (start.toLocalDate().isBefore(firstDate) || start.toLocalDate().isAfter(lastDate)) {
            throw new IllegalArgumentException("Appointment must be scheduled today or within the next three days");
        }
        if (start.isBefore(now)) {
            throw new IllegalArgumentException("Appointment slot must be in the future");
        }
        LocalTime localStart = start.toLocalTime();
        if (localStart.isBefore(LocalTime.of(8, 0)) ||
            !localStart.isBefore(LocalTime.of(18, 0)) ||
            localStart.getMinute() % 15 != 0 ||
            localStart.getSecond() != 0 ||
            localStart.getNano() != 0) {
            throw new IllegalArgumentException("Appointment must start on a 15-minute boundary between 08:00 and 18:00 Stockholm time");
        }

        ZonedDateTime end = start.plusMinutes(recommendation.getDurationMinutes());
        LocalTime clinicianStart = STARTS.stream()
            .filter(candidate -> coversWorkPeriod(candidate, start.toLocalTime(), end.toLocalTime()))
            .filter(candidate -> bookings.stream().noneMatch(booking ->
                booking.clinicianStart().equals(candidate) &&
                overlaps(start.toInstant(), end.toInstant(), booking.start(), booking.end())))
            .findFirst()
            .orElseThrow(() -> new NoAvailabilityException("No clinician is available for the requested appointment"));
        bookings.add(new Booking(start.toInstant(), end.toInstant(), clinicianStart));
        return UUID.randomUUID().toString();
    }

    public synchronized String book(OffsetDateTime slot) {
        return book(slot, Recommendation.Chat);
    }

    public static class NoAvailabilityException extends RuntimeException {
        public NoAvailabilityException(String message) {
            super(message);
        }
    }

    private record Booking(Instant start, Instant end, LocalTime clinicianStart) {}
}
