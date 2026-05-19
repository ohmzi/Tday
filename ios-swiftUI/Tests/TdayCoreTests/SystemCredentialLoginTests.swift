import XCTest

#if SWIFT_PACKAGE
@testable import TdayCore
#else
@testable import Tday
#endif

@MainActor
final class SystemCredentialLoginTests: XCTestCase {
    func testNoSavedCredentialDoesNotSubmitLogin() async {
        let service = FakeSystemCredentialService(nextCredential: nil)
        let coordinator = LoginCredentialCoordinator()
        var submittedCredentials: [SystemCredential] = []

        let didSubmit = await coordinator.requestSavedCredentialIfAvailable(
            currentPassword: "",
            isCreatingAccount: false,
            isAuthLoading: false,
            credentialService: service
        ) { credential in
            submittedCredentials.append(credential)
            return true
        }

        XCTAssertFalse(didSubmit)
        XCTAssertEqual(service.requestCount, 1)
        XCTAssertTrue(submittedCredentials.isEmpty)
    }

    func testSavedCredentialSubmitsLogin() async {
        let credential = SystemCredential(email: "user@example.com", password: "Password!1")
        let service = FakeSystemCredentialService(nextCredential: credential)
        let coordinator = LoginCredentialCoordinator()
        var submittedCredentials: [SystemCredential] = []

        let didSubmit = await coordinator.requestSavedCredentialIfAvailable(
            currentPassword: "",
            isCreatingAccount: false,
            isAuthLoading: false,
            credentialService: service
        ) { credential in
            submittedCredentials.append(credential)
            return true
        }

        XCTAssertTrue(didSubmit)
        XCTAssertEqual(submittedCredentials, [credential])
    }

    func testUserCancelLeavesManualFormUntouched() async {
        let service = FakeSystemCredentialService(nextCredential: nil)
        let coordinator = LoginCredentialCoordinator()
        var didSubmitLogin = false

        let didSubmit = await coordinator.requestSavedCredentialIfAvailable(
            currentPassword: "",
            isCreatingAccount: false,
            isAuthLoading: false,
            credentialService: service
        ) { _ in
            didSubmitLogin = true
            return true
        }

        XCTAssertFalse(didSubmit)
        XCTAssertFalse(didSubmitLogin)
    }

    func testManualLoginOffersSaveOrUpdateCredentialAfterSuccess() async {
        let repository = FakeAuthRepository(result: .success)
        let service = FakeSystemCredentialService()
        let viewModel = AuthViewModel(
            authRepository: repository,
            systemCredentialService: service
        )

        let success = await viewModel.login(email: " USER@example.com ", password: "Password!1", source: .manual)

        XCTAssertTrue(success)
        XCTAssertEqual(service.offeredCredentials, [SystemCredential(email: "user@example.com", password: "Password!1")])
    }

    func testManualLoginOffersUpdateWhenEmailMatchesExistingSavedEmail() async {
        let repository = FakeAuthRepository(result: .success, lastEmail: "user@example.com")
        let service = FakeSystemCredentialService()
        let viewModel = AuthViewModel(
            authRepository: repository,
            systemCredentialService: service
        )

        let success = await viewModel.login(email: "user@example.com", password: "NewPassword!1", source: .manual)

        XCTAssertTrue(success)
        XCTAssertEqual(viewModel.savedEmail, "user@example.com")
        XCTAssertEqual(service.offeredCredentials, [SystemCredential(email: "user@example.com", password: "NewPassword!1")])
    }

    func testManualLoginKeepsPasswordSaveFailureNonBlocking() async {
        let repository = FakeAuthRepository(result: .success)
        let service = FakeSystemCredentialService(saveResult: .failed("Apple Passwords could not save this login."))
        let viewModel = AuthViewModel(
            authRepository: repository,
            systemCredentialService: service
        )

        let success = await viewModel.login(email: "user@example.com", password: "Password!1", source: .manual)

        XCTAssertTrue(success)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertNil(viewModel.infoMessage)
        XCTAssertEqual(service.offeredCredentials, [SystemCredential(email: "user@example.com", password: "Password!1")])
    }

    func testRegistrationOffersSaveOrUpdateCredentialAfterSuccess() async {
        let repository = FakeAuthRepository(result: .success)
        let service = FakeSystemCredentialService()
        let viewModel = AuthViewModel(
            authRepository: repository,
            systemCredentialService: service
        )

        let success = await viewModel.register(firstName: "Test", lastName: "", email: " USER@example.com ", password: "Password!1")

        XCTAssertTrue(success)
        XCTAssertEqual(service.offeredCredentials, [SystemCredential(email: "user@example.com", password: "Password!1")])
    }

    func testFailedLoginDoesNotOfferSaveOrUpdateCredential() async {
        let repository = FakeAuthRepository(result: .error("Invalid credentials"))
        let service = FakeSystemCredentialService()
        let viewModel = AuthViewModel(
            authRepository: repository,
            systemCredentialService: service
        )

        let success = await viewModel.login(email: "user@example.com", password: "Password!1", source: .manual)

        XCTAssertFalse(success)
        XCTAssertTrue(service.offeredCredentials.isEmpty)
    }

    func testPendingApprovalDoesNotOfferSaveOrUpdateCredential() async {
        let repository = FakeAuthRepository(result: .pendingApproval)
        let service = FakeSystemCredentialService()
        let viewModel = AuthViewModel(
            authRepository: repository,
            systemCredentialService: service
        )

        let success = await viewModel.login(email: "user@example.com", password: "Password!1", source: .manual)

        XCTAssertFalse(success)
        XCTAssertTrue(service.offeredCredentials.isEmpty)
    }

    func testSystemCredentialLoginDoesNotOfferSaveOrUpdateCredential() async {
        let repository = FakeAuthRepository(result: .success)
        let service = FakeSystemCredentialService()
        let viewModel = AuthViewModel(
            authRepository: repository,
            systemCredentialService: service
        )

        let success = await viewModel.login(email: "user@example.com", password: "Password!1", source: .systemPasswordAutoFill)

        XCTAssertTrue(success)
        XCTAssertTrue(service.offeredCredentials.isEmpty)
    }
}

@MainActor
private final class FakeSystemCredentialService: SystemCredentialServicing {
    var requestCount = 0
    var offeredCredentials: [SystemCredential] = []
    var nextCredential: SystemCredential?
    var saveResult: SystemCredentialSaveResult

    init(nextCredential: SystemCredential? = nil, saveResult: SystemCredentialSaveResult = .saved) {
        self.nextCredential = nextCredential
        self.saveResult = saveResult
    }

    func requestSavedCredential() async -> SystemCredential? {
        requestCount += 1
        return nextCredential
    }

    func offerSaveOrUpdateCredential(_ credential: SystemCredential) async -> SystemCredentialSaveResult {
        offeredCredentials.append(credential)
        return saveResult
    }
}

private final class FakeAuthRepository: AuthRepositoryServicing {
    let result: AuthResult
    let storedLastEmail: String?

    init(result: AuthResult, lastEmail: String? = nil) {
        self.result = result
        storedLastEmail = lastEmail
    }

    func restoreSession() async -> SessionUser? {
        nil
    }

    func login(email: String, password: String) async -> AuthResult {
        result
    }

    func register(firstName: String, lastName: String, email: String, password: String) async -> RegisterOutcome {
        RegisterOutcome(success: true, requiresApproval: false, message: "Account created")
    }

    func logout() async {}

    func syncTimezone() async {}

    @MainActor
    func clearSessionOnly() {}

    @MainActor
    func clearAllLocalUserDataForUnauthenticatedState() {}

    func getLastEmail() -> String? {
        storedLastEmail
    }

    func lastEmail() -> String? {
        storedLastEmail
    }
}
