package pet.kry.challenge;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingServiceTest {
    private static final OffsetDateTime OPENING = OffsetDateTime.parse("2026-10-12T08:00:00+02:00");
    private SchedulingService newScheduling() {
        return new SchedulingService(
            Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneId.of("Europe/Stockholm")));
    }

    @Test void hasCapacityAtOpening() {
        assertTrue(newScheduling().availableSlots(Recommendation.Chat).contains(OPENING));
    }

    @Test void hasSlotsInTheFuture() {
        assertFalse(newScheduling().availableSlots(Recommendation.Chat).isEmpty());
    }

    @Test void mapsChatDuration() {
        assertEquals(15, Recommendation.Chat.getDurationMinutes());
        assertEquals(Recommendation.Chat, Recommendation.from("Chat"));
    }

    @Test void mapsNurseDuration() {
        assertEquals(30, Recommendation.Nurse.getDurationMinutes());
        assertEquals(Recommendation.Nurse, Recommendation.from(" nurse "));
    }

    @Test void mapsDoctorDuration() {
        assertEquals(45, Recommendation.Doctor.getDurationMinutes());
        assertEquals(Recommendation.Doctor, Recommendation.from("doctor"));
    }

    @Test void rejectsUnsupportedRecommendation() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Recommendation.from("Therapist"));
        assertTrue(ex.getMessage().contains("Unsupported recommendation"));
        assertTrue(ex.getMessage().contains("Chat, Nurse, Doctor"));
    }

    @Test void bookingRejectsUnsupportedRecommendationAtApiBoundary() {
        AssessmentController controller = new AssessmentController(newScheduling());
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> controller.book(new AssessmentController.BookingRequest(OPENING, "Therapist"))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Unsupported recommendation"));
    }

    @Test void bookingReturnsBadRequestForInvalidSlotAndConflictWhenUnavailable() {
        SchedulingService scheduling = newScheduling();
        AssessmentController controller = new AssessmentController(scheduling);

        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
            () -> controller.book(new AssessmentController.BookingRequest(OPENING.withMinute(1), "Chat")));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());

        scheduling.book(OPENING, Recommendation.Nurse);
        ResponseStatusException unavailable = assertThrows(ResponseStatusException.class,
            () -> controller.book(new AssessmentController.BookingRequest(OPENING.plusMinutes(15), "Nurse")));
        assertEquals(HttpStatus.CONFLICT, unavailable.getStatusCode());
        assertTrue(unavailable.getReason().contains("No clinician is available"));
    }

    @Test void malformedBookingBodyGetsGenericBadRequest() {
        ResponseStatusException ex = new AssessmentController(newScheduling()).invalidRequestBody();
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invalid request body or appointment slot", ex.getReason());
    }

    @Test void availabilitySupportsAllAppointmentDurations() {
        SchedulingService scheduling = newScheduling();
        assertFalse(scheduling.availableSlots(Recommendation.Chat).isEmpty());
        assertFalse(scheduling.availableSlots(Recommendation.Nurse).isEmpty());
        assertFalse(scheduling.availableSlots(Recommendation.Doctor).isEmpty());
    }

    @Test void appointmentCrossingBreakDoesNotFitThatClinician() {
        assertFalse(SchedulingService.coversWorkPeriod(
            LocalTime.of(8, 0), LocalTime.of(11, 45), LocalTime.of(12, 15)));
    }

    @Test void appointmentPastShiftEndDoesNotFitThatClinician() {
        assertFalse(SchedulingService.coversWorkPeriod(
            LocalTime.of(9, 0), LocalTime.of(17, 30), LocalTime.of(18, 15)));
    }

    @Test void overlappingBookingBlocksThatClinician() {
        SchedulingService scheduling = newScheduling();
        scheduling.book(OPENING, Recommendation.Doctor);
        assertFalse(scheduling.availableSlots(Recommendation.Doctor).contains(OPENING));
        assertThrows(SchedulingService.NoAvailabilityException.class,
            () -> scheduling.book(OPENING, Recommendation.Chat));
    }

    @Test void backToBackAppointmentsAreAllowed() {
        SchedulingService scheduling = newScheduling();
        scheduling.book(OPENING, Recommendation.Chat);
        assertDoesNotThrow(() -> scheduling.book(OPENING.plusMinutes(15), Recommendation.Chat));
    }

    @Test void validBookingSucceedsAndConsumesCapacity() {
        SchedulingService scheduling = newScheduling();
        String confirmation = scheduling.book(OPENING, Recommendation.Chat);
        assertNotNull(confirmation);
        assertFalse(scheduling.availableSlots(Recommendation.Chat).contains(OPENING));
    }

    @Test void rejectsNullPastMisalignedAndOutsideHorizonSlots() {
        SchedulingService scheduling = newScheduling();
        assertThrows(IllegalArgumentException.class, () -> scheduling.book(null, Recommendation.Chat));
        assertThrows(IllegalArgumentException.class,
            () -> scheduling.book(OPENING.minusDays(1), Recommendation.Chat));
        assertThrows(IllegalArgumentException.class,
            () -> scheduling.book(OPENING.withMinute(1), Recommendation.Chat));
        assertThrows(IllegalArgumentException.class,
            () -> scheduling.book(OPENING.plusDays(4), Recommendation.Chat));
    }

    @Test void rejectsUnavailableSlotWithoutChangingBookingState() {
        SchedulingService scheduling = newScheduling();
        scheduling.book(OPENING, Recommendation.Nurse);
        assertThrows(SchedulingService.NoAvailabilityException.class,
            () -> scheduling.book(OPENING.plusMinutes(15), Recommendation.Nurse));
        assertFalse(scheduling.availableSlots(Recommendation.Chat).contains(OPENING));
        assertTrue(scheduling.availableSlots(Recommendation.Chat).contains(OPENING.plusMinutes(30)));
    }

    @Test void longerBookingBlocksConflictingAppointment() {
        SchedulingService scheduling = newScheduling();
        scheduling.book(OPENING, Recommendation.Doctor);
        assertThrows(SchedulingService.NoAvailabilityException.class,
            () -> scheduling.book(OPENING.plusMinutes(15), Recommendation.Chat));
    }

    @Test void concurrentBookingsCannotOverbookRemainingCapacity() throws Exception {
        SchedulingService scheduling = newScheduling();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                ready.countDown();
                start.await();
                try {
                    scheduling.book(OPENING, Recommendation.Chat);
                    return true;
                } catch (SchedulingService.NoAvailabilityException e) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))
                .stream().filter(Boolean::booleanValue).count());
            assertFalse(scheduling.availableSlots(Recommendation.Chat).contains(OPENING));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void assessmentReturnsRecommendationSpecificAvailability() {
        SchedulingService scheduling = newScheduling();
        scheduling.book(OPENING.plusMinutes(15), Recommendation.Chat);
        AssessmentController controller = new AssessmentController(scheduling);
        var chat = controller.assess(new AssessmentController.AssessmentRequest(List.of(1, 1, 1, 1, 1)));
        var nurse = controller.assess(new AssessmentController.AssessmentRequest(List.of(3, 2, 1, 1, 1)));
        var doctor = controller.assess(new AssessmentController.AssessmentRequest(List.of(3, 3, 2, 2, 2)));

        assertEquals("Chat", chat.recommendation());
        assertEquals("Nurse", nurse.recommendation());
        assertEquals("Doctor", doctor.recommendation());
        assertTrue(chat.availableSlots().contains(OPENING));
        assertFalse(nurse.availableSlots().contains(OPENING));
        assertFalse(doctor.availableSlots().contains(OPENING));
    }
}
