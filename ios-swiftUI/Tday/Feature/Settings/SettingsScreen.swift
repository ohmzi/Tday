import SwiftUI

struct SettingsScreen: View {
    let viewModel: AppViewModel
    let onBack: () -> Void
    @Environment(\.tdayColors) private var colors

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Text(viewModel.user?.name ?? "Unknown user")
                        .font(.headline)
                    Text(viewModel.user?.email ?? "")
                        .font(.subheadline)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Text("Role: \(viewModel.user?.role ?? "USER")")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                .padding(.vertical, 4)
            }

            Section("Appearance") {
                Picker("Theme", selection: Binding(get: { viewModel.themeMode }, set: { viewModel.setThemeMode($0) })) {
                    ForEach(AppThemeMode.allCases, id: \.self) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
            }

            Section("Reminders") {
                Picker("Default reminder", selection: Binding(get: { viewModel.selectedReminder }, set: { viewModel.setDefaultReminder($0) })) {
                    ForEach(ReminderOption.allCases) { option in
                        Text(option.label).tag(option)
                    }
                }
            }

            if viewModel.user?.role?.uppercased() == "ADMIN" {
                Section("Admin") {
                    Toggle(
                        "AI task summary",
                        isOn: Binding(
                            get: { viewModel.adminAiSummaryEnabled ?? false },
                            set: { newValue in
                                Task { await viewModel.setAdminAiSummaryEnabled(newValue) }
                            }
                        )
                    )
                    .disabled(viewModel.isAdminAiSummaryLoading || viewModel.isAdminAiSummarySaving)

                    if let adminAiSummaryError = viewModel.adminAiSummaryError {
                        Text(adminAiSummaryError)
                            .font(.footnote)
                            .foregroundStyle(colors.error)
                    }
                }
            }

            Section {
                HStack {
                    Text("Version")
                    Spacer()
                    Text("v\(viewModel.currentVersionName)")
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                if viewModel.hasUpdate, let latest = viewModel.latestVersionName {
                    Text("v\(latest) available")
                        .font(.footnote)
                        .foregroundStyle(colors.primary)
                }
                if let backendVersion = viewModel.backendVersion {
                    HStack {
                        Text("Server")
                        Spacer()
                        HStack(spacing: 8) {
                            Text("v\(backendVersion)")
                                .foregroundStyle(colors.onSurfaceVariant)
                            let isCompatible = viewModel.versionCheckResult == .compatible
                            Text(isCompatible ? "Compatible" : "Incompatible")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(isCompatible ? Color.green : colors.error)
                        }
                    }
                }
            }

            Section {
                Button(role: .destructive) {
                    Task { await viewModel.logout() }
                } label: {
                    Text("Sign Out")
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(colors.background)
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Back", action: onBack)
            }
        }
        .task {
            await viewModel.refreshAdminAiSummarySetting()
        }
        .alert("Validation Error", isPresented: Binding(get: { viewModel.aiSummaryValidationError != nil }, set: { visible in
            if !visible {
                viewModel.dismissAiSummaryValidationError()
            }
        })) {
            Button("OK", role: .cancel) {
                viewModel.dismissAiSummaryValidationError()
            }
        } message: {
            Text(viewModel.aiSummaryValidationError ?? "")
        }
    }
}
