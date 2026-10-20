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

    public List<OffsetDateTime> availableSlots(Recommendation recommendation) {
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

    public List<OffsetDateTime> availableSlots() {
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

    public String book(OffsetDateTime slot, Recommendation recommendation) {
        ZonedDateTime start = slot.atZoneSameInstant(CLINIC_ZONE);
        ZonedDateTime end = start.plusMinutes(recommendation.getDurationMinutes());
        LocalTime clinicianStart = STARTS.stream()
            .filter(candidate -> coversWorkPeriod(candidate, start.toLocalTime(), end.toLocalTime()))
            .filter(candidate -> bookings.stream().noneMatch(booking ->
                booking.clinicianStart().equals(candidate) &&
                overlaps(start.toInstant(), end.toInstant(), booking.start(), booking.end())))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No clinician is available for the requested appointment"));
        bookings.add(new Booking(start.toInstant(), end.toInstant(), clinicianStart));
        return UUID.randomUUID().toString();
    }

    public String book(OffsetDateTime slot) {
        return book(slot, Recommendation.Chat);
    }

    private record Booking(Instant start, Instant end, LocalTime clinicianStart) {}
}
