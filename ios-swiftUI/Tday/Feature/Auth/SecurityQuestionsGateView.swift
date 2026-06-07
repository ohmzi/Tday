import SwiftUI

/// Blocking prompt shown to accounts created before security questions existed.
/// They must choose two questions before continuing so self-service password
/// reset works for them. The backend clears the flag on success; the caller
/// refreshes the session afterwards which dismisses this gate.
struct SecurityQuestionsGateView: View {
    let authViewModel: AuthViewModel
    let onSaved: () async -> Void

    @Environment(\.tdayColors) private var colors

    @State private var questions: [SecurityQuestion] = []
    @State private var questionId1: Int?
    @State private var questionId2: Int?
    @State private var questionId3: Int?
    @State private var answer1 = ""
    @State private var answer2 = ""
    @State private var answer3 = ""
    @State private var isLoading = true
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var canSubmit: Bool {
        guard let id1 = questionId1, let id2 = questionId2, let id3 = questionId3,
              Set([id1, id2, id3]).count == 3 else {
            return false
        }
        return !answer1.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !answer2.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !answer3.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !isSaving
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            Image(systemName: "lock.shield.fill")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(colors.primary)
                            Text(L("Set up security questions"))
                                .font(.tdayRounded(size: 20, weight: .heavy))
                                .foregroundStyle(colors.onSurface)
                        }

                        Text(L("Choose three security questions. We'll use them to verify it's you if you ever need to reset your password."))
                            .font(.tdayRounded(size: 14, weight: .bold))
                            .foregroundStyle(colors.onSurface.opacity(0.62))
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if isLoading {
                        ProgressView()
                            .tint(colors.primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 24)
                    } else {
                        SecurityQuestionMenu(
                            title: "Question 1",
                            selection: $questionId1,
                            options: questions.filter { $0.id != questionId2 && $0.id != questionId3 }
                        )
                        SecurityGateField(title: "Answer", text: $answer1)

                        SecurityQuestionMenu(
                            title: "Question 2",
                            selection: $questionId2,
                            options: questions.filter { $0.id != questionId1 && $0.id != questionId3 }
                        )
                        SecurityGateField(title: "Answer", text: $answer2)

                        SecurityQuestionMenu(
                            title: "Question 3",
                            selection: $questionId3,
                            options: questions.filter { $0.id != questionId1 && $0.id != questionId2 }
                        )
                        SecurityGateField(title: "Answer", text: $answer3)
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.tdayRounded(size: 14, weight: .bold))
                            .foregroundStyle(colors.error)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Button {
                        Task { await save() }
                    } label: {
                        Text(L(isSaving ? "Saving..." : "Save security questions"))
                            .font(.tdayRounded(size: 15, weight: .bold))
                            .foregroundStyle(canSubmit ? colors.onPrimary : colors.onSurfaceVariant.opacity(0.65))
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background {
                                Capsule(style: .continuous)
                                    .fill(canSubmit ? colors.primary : colors.surfaceVariant.opacity(0.95))
                            }
                    }
                    .buttonStyle(.plain)
                    .opacity(canSubmit ? 1 : 0.72)
                    .disabled(!canSubmit)
                }
                .padding(20)
                .frame(maxWidth: 430, alignment: .leading)
                .background {
                    RoundedRectangle(cornerRadius: 30, style: .continuous)
                        .fill(colors.background)
                        .overlay(
                            RoundedRectangle(cornerRadius: 30, style: .continuous)
                                .stroke(colors.onSurface.opacity(colors.isDark ? 0.12 : 0.08), lineWidth: 1)
                        )
                }
                .shadow(color: Color.black.opacity(colors.isDark ? 0.34 : 0.14), radius: 18, x: 0, y: 10)
                .padding(18)
            }
        }
        .task {
            await loadQuestions()
        }
    }

    private func loadQuestions() async {
        guard questions.isEmpty else {
            return
        }
        isLoading = true
        let loaded = await authViewModel.loadAllSecurityQuestions()
        isLoading = false
        questions = loaded
        if questionId1 == nil, loaded.indices.contains(0) {
            questionId1 = loaded[0].id
        }
        if questionId2 == nil, loaded.indices.contains(1) {
            questionId2 = loaded[1].id
        }
        if questionId3 == nil, loaded.indices.contains(2) {
            questionId3 = loaded[2].id
        }
        if loaded.isEmpty {
            errorMessage = "Could not load security questions. Please try again."
        }
    }

    private func save() async {
        errorMessage = nil
        guard let id1 = questionId1, let id2 = questionId2, let id3 = questionId3,
              Set([id1, id2, id3]).count == 3 else {
            errorMessage = "Choose three different questions"
            return
        }
        let trimmed1 = answer1.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmed2 = answer2.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmed3 = answer3.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed1.isEmpty, !trimmed2.isEmpty, !trimmed3.isEmpty else {
            errorMessage = "Please answer all three questions"
            return
        }

        isSaving = true
        let didSave = await authViewModel.setSecurityQuestions([
            SecurityAnswerInput(questionId: id1, answer: trimmed1),
            SecurityAnswerInput(questionId: id2, answer: trimmed2),
            SecurityAnswerInput(questionId: id3, answer: trimmed3),
        ])
        isSaving = false
        if didSave {
            await onSaved()
        } else {
            errorMessage = "Failed to save security questions"
        }
    }
}

private struct SecurityQuestionMenu: View {
    let title: String
    @Binding var selection: Int?
    let options: [SecurityQuestion]

    @Environment(\.tdayColors) private var colors

    private var selectedText: String {
        guard let selection, let match = options.first(where: { $0.id == selection }) else {
            return L("Choose a question")
        }
        return match.text
    }

    var body: some View {
        Menu {
            ForEach(options) { question in
                Button {
                    selection = question.id
                } label: {
                    if selection == question.id {
                        Label(question.text, systemImage: "checkmark")
                    } else {
                        Text(question.text)
                    }
                }
            }
        } label: {
            HStack(spacing: 10) {
                Text(selectedText)
                    .font(.tdayRounded(size: 15, weight: .bold))
                    .foregroundStyle(selection == nil ? colors.onSurface.opacity(0.42) : colors.onSurface)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Spacer(minLength: 0)

                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(colors.onSurface.opacity(0.5))
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: 54)
            .background {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(colors.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .stroke(colors.onSurface.opacity(0.14), lineWidth: 1)
                    )
            }
        }
        .accessibilityLabel(L(title))
    }
}

private struct SecurityGateField: View {
    let title: String
    @Binding var text: String

    @Environment(\.tdayColors) private var colors
    @FocusState private var isFocused: Bool

    var body: some View {
        TextField(
            "",
            text: $text,
            prompt: Text(L(title)).foregroundStyle(colors.onSurface.opacity(0.42))
        )
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled(true)
        .focused($isFocused)
        .font(.tdayRounded(size: 15, weight: .bold))
        .foregroundStyle(colors.onSurface)
        .tint(colors.primary)
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 54)
        .background {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(colors.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .stroke(
                            isFocused ? colors.primary.opacity(0.82) : colors.onSurface.opacity(0.14),
                            lineWidth: isFocused ? 1.1 : 1
                        )
                )
        }
        .accessibilityLabel(L(title))
    }
}
