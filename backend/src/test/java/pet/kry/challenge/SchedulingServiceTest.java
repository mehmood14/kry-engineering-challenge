package pet.kry.challenge;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingServiceTest {
    private final SchedulingService scheduling = new SchedulingService(
        Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneId.of("Europe/Stockholm")));

    @Test void hasCapacityAtOpening() {
        assertEquals(1, scheduling.activeClinicians(LocalTime.of(8, 0)));
    }

    @Test void hasSlotsInTheFuture() {
        assertFalse(scheduling.availableSlots().isEmpty());
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
        AssessmentController controller = new AssessmentController(scheduling);
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> controller.book(new AssessmentController.BookingRequest(OffsetDateTime.parse("2026-10-12T08:00:00+02:00"), "Therapist"))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Unsupported recommendation"));
    }
}
