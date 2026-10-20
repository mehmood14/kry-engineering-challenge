package pet.kry.challenge;

public enum Recommendation {
    Chat(15),
    Nurse(30),
    Doctor(45);

    private final int durationMinutes;

    Recommendation(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public static Recommendation from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Recommendation is required");
        }
        String normalized = value.trim();
        for (Recommendation recommendation : values()) {
            if (recommendation.name().equalsIgnoreCase(normalized)) {
                return recommendation;
            }
        }
        throw new IllegalArgumentException("Unsupported recommendation: " + value + ". Supported: Chat, Nurse, Doctor");
    }
}
