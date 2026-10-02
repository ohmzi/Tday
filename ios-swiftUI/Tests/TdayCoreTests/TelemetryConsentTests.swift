import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

/// The per-device answer to "send crash reports?", against a suite of its own.
final class TelemetryConsentStoreTests: XCTestCase {
    private var suiteName: String!
    private var defaults: UserDefaults!

    override func setUp() {
        super.setUp()
        suiteName = "com.ohmz.tday.tests.telemetry-consent.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        super.tearDown()
    }

    func testNothingStoredIsUnansweredAndBehavesAsOff() {
        let store = TelemetryConsentStore(defaults: defaults)

        XCTAssertEqual(store.state, .unanswered)
        XCTAssertFalse(store.isGranted)
        XCTAssertNil(store.consentedAt)
    }

    func testGrantRecordsTheAnswerAndWhen() {
        let store = TelemetryConsentStore(defaults: defaults)
        let moment = Date(timeIntervalSince1970: 1_800_000_000)

        store.grant(at: moment)

        XCTAssertEqual(store.state, .granted)
        XCTAssertTrue(store.isGranted)
        XCTAssertEqual(store.consentedAt, moment)
    }

    func testDenyRecordsTheAnswerAndForgetsWhen() {
        let store = TelemetryConsentStore(defaults: defaults)
        store.grant()

        store.deny()

        XCTAssertEqual(store.state, .denied)
        XCTAssertFalse(store.isGranted)
        XCTAssertNil(store.consentedAt)
    }

    func testGrantingAgainAfterDenyingStartsANewClock() {
        let store = TelemetryConsentStore(defaults: defaults)
        store.grant(at: Date(timeIntervalSince1970: 1_800_000_000))
        store.deny()

        store.grant(at: Date(timeIntervalSince1970: 1_900_000_000))

        XCTAssertEqual(store.consentedAt, Date(timeIntervalSince1970: 1_900_000_000))
    }

    func testAnotherStoreOverTheSameDefaultsReadsTheSameAnswer() {
        TelemetryConsentStore(defaults: defaults).grant()

        XCTAssertEqual(TelemetryConsentStore(defaults: defaults).state, .granted)
    }

    /// The keys are part of the contract with the other clients' stores and with
    /// `docs/DATA_MODEL.md`; renaming one silently turns everyone's answer back into "unanswered".
    func testStoresTheDocumentedKeys() {
        let store = TelemetryConsentStore(defaults: defaults)
        store.grant(at: Date(timeIntervalSince1970: 1_800_000_000))

        XCTAssertEqual(defaults.object(forKey: "telemetry.consent") as? Bool, true)
        XCTAssertEqual(defaults.object(forKey: "telemetry.consentAt") as? Double, 1_800_000_000)

        store.deny()

        XCTAssertEqual(defaults.object(forKey: "telemetry.consent") as? Bool, false)
        XCTAssertNil(defaults.object(forKey: "telemetry.consentAt"))
    }
}

/// What answering does, and when the card is due. The grant and revoke steps are spies; the real
/// ones start and stop an SDK.
@MainActor
final class TelemetryConsentModelTests: XCTestCase {
    private var suiteName: String!
    private var defaults: UserDefaults!
    private var store: TelemetryConsentStore!
    private var grants = 0
    private var revokes = 0

    override func setUp() {
        super.setUp()
        suiteName = "com.ohmz.tday.tests.telemetry-model.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        store = TelemetryConsentStore(defaults: defaults)
        grants = 0
        revokes = 0
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        store = nil
        defaults = nil
        super.tearDown()
    }

    /// The same two writes the real lifecycle makes, so the model reads back what it would in the app.
    private func makeModel(isAvailable: Bool = true) -> TelemetryConsentModel {
        TelemetryConsentModel(
            store: store,
            isAvailable: isAvailable,
            grant: { [unowned self] store in
                store.grant()
                self.grants += 1
            },
            revoke: { [unowned self] store in
                store.deny()
                self.revokes += 1
            }
        )
    }

    private func isCardDue(_ model: TelemetryConsentModel, workspace: Bool = true, covered: Bool = false) -> Bool {
        model.shouldPresentCard(workspaceAvailable: workspace, isCoveredByAnotherGate: covered)
    }

    // MARK: - When the card is due

    func testTheCardIsDueOnceTheWorkspaceIsOpenAndNothingAnsweredIt() {
        XCTAssertTrue(isCardDue(makeModel()))
    }

    func testTheCardWaitsForAWorkspaceAndForEveryGateThatComesFirst() {
        let model = makeModel()

        XCTAssertFalse(isCardDue(model, workspace: false))
        XCTAssertFalse(isCardDue(model, covered: true))
    }

    func testABuildWithoutADsnNeverShowsTheCardAndCannotBeSwitchedOn() {
        let model = makeModel(isAvailable: false)

        XCTAssertFalse(isCardDue(model))
        model.share()
        XCTAssertEqual(model.state, .unanswered)
        XCTAssertEqual(grants, 0)
    }

    func testAnAnswerFromAnEarlierLaunchIsNotAskedAgain() {
        store.deny()

        let model = makeModel()

        XCTAssertEqual(model.state, .denied)
        XCTAssertFalse(isCardDue(model))
    }

    func testReadingTheFaqHoldsTheCardBackWithoutAnsweringIt() {
        let model = makeModel()

        model.deferForSession()

        XCTAssertFalse(isCardDue(model))
        XCTAssertEqual(model.state, .unanswered)
        XCTAssertEqual(store.state, .unanswered)
        // The next launch is a new model over the same store, and asks.
        XCTAssertTrue(isCardDue(makeModel()))
    }

    // MARK: - Answering

    func testSharingGrantsOnceAndTheCardGoesAway() {
        let model = makeModel()

        model.share()
        model.share()

        XCTAssertEqual(model.state, .granted)
        XCTAssertTrue(model.isEnabled)
        XCTAssertEqual(grants, 1)
        XCTAssertFalse(isCardDue(model))
    }

    func testNotNowRecordsADenialEvenThoughNothingWasRunning() {
        let model = makeModel()

        model.decline()

        XCTAssertEqual(model.state, .denied)
        XCTAssertFalse(model.isEnabled)
        XCTAssertEqual(store.state, .denied)
        XCTAssertEqual(revokes, 1)
        XCTAssertFalse(isCardDue(model))
    }

    func testSwitchingOffAfterSharingRevokesAndSwitchingBackOnGrantsAgain() {
        let model = makeModel()
        model.share()

        model.decline()
        XCTAssertFalse(model.isEnabled)
        XCTAssertEqual(revokes, 1)

        model.share()
        XCTAssertTrue(model.isEnabled)
        XCTAssertEqual(grants, 2)
    }

    func testAnsweringInSettingsFirstMeansTheCardIsNeverDue() {
        let model = makeModel()

        model.share()
        model.decline()

        XCTAssertFalse(isCardDue(model))
    }
}
