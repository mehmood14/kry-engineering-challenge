package pet.kry.challenge;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
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
        assertThrows(IllegalArgumentException.class, () -> scheduling.book(OPENING, Recommendation.Chat));
    }

    @Test void backToBackAppointmentsAreAllowed() {
        SchedulingService scheduling = newScheduling();
        scheduling.book(OPENING, Recommendation.Chat);
        assertDoesNotThrow(() -> scheduling.book(OPENING.plusMinutes(15), Recommendation.Chat));
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
