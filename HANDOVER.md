# Handover

## Priorities
I prioritized making appointment type affect the entire booking journey: availability, conflict checking, booking validation, and the information shown to patients. This addresses the core Scenario B requirement while preserving the existing questionnaire and API flow.

## Feature decision
I retained a 15-minute candidate start-time grid and represented each in-memory booking as a half-open time interval assigned to a clinician's shift. This supports 15-, 30-, and 45-minute appointments, respects break boundaries, and permits back-to-back bookings. I rejected a general interval-scheduling framework as unnecessary for this single-clinic POC.

## Deliberately unfinished
Bookings are in-memory. Synchronization prevents conflicting reservations within a single application process but does not provide persistence or coordination across multiple instances. Cancellation/rescheduling, multiple clinics, and a dedicated frontend test framework are outside this feature slice.

## Verification
- `cd backend && ./gradlew test --console=plain` — `BUILD SUCCESSFUL`; 20 tests, 0 failures, 0 errors (Gradle reported tasks up-to-date).
- `cd frontend && npm run build` — passed TypeScript checks and the Vite production build.

The frontend has no existing test or lint script, so neither was run.