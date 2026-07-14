import UIKit
import UniformTypeIdentifiers

/// Receives text/URL shares from other apps' share sheets. Share extensions
/// cannot open their host app and this one deliberately does no networking or
/// SwiftData: it maps the share to a `{title, notes}` descriptor, appends it
/// to the `tday.share.pendingShares` app-group queue, confirms with a brief
/// overlay, and dismisses. The app drains the queue into a prefilled
/// create-task sheet on its next activation (see PendingShareStore in the app
/// target — key and payload shape must stay in lockstep).
final class ShareViewController: UIViewController {

    private static let appGroupSuiteName = "group.com.ohmz.tday"
    private static let queueKey = "tday.share.pendingShares"

    private struct PendingShare: Codable {
        let title: String
        let notes: String?
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.black.withAlphaComponent(0.25)
        loadSharedContent { [weak self] share in
            DispatchQueue.main.async {
                guard let self else {
                    return
                }
                guard let share else {
                    self.extensionContext?.cancelRequest(withError: NSError(
                        domain: "com.ohmz.tday.share",
                        code: 1,
                        userInfo: [NSLocalizedDescriptionKey: "Nothing shareable found"]
                    ))
                    return
                }
                Self.enqueue(share)
                self.showConfirmationAndFinish()
            }
        }
    }

    // MARK: - Share extraction

    /// Collects the first text and/or URL attachment plus the item's title,
    /// then applies the same precedence as Android's sharedTaskContent():
    /// a distinct subject becomes the task title with the text/URL as notes;
    /// bare text is itself the title.
    private func loadSharedContent(completion: @escaping (PendingShare?) -> Void) {
        guard let item = (extensionContext?.inputItems as? [NSExtensionItem])?.first else {
            completion(nil)
            return
        }
        let subject = item.attributedTitle?.string
        let contentText = item.attributedContentText?.string
        let providers = item.attachments ?? []

        if let urlProvider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.url.identifier) }) {
            urlProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { value, _ in
                let url = (value as? URL)?.absoluteString ?? (value as? Data).flatMap {
                    URL(dataRepresentation: $0, relativeTo: nil)?.absoluteString
                }
                completion(Self.pendingShare(subject: subject ?? contentText, text: url ?? contentText))
            }
            return
        }

        if let textProvider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) }) {
            textProvider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { value, _ in
                let text = (value as? String) ?? (value as? Data).flatMap { String(data: $0, encoding: .utf8) }
                completion(Self.pendingShare(subject: subject, text: text ?? contentText))
            }
            return
        }

        completion(Self.pendingShare(subject: subject, text: contentText))
    }

    private static func pendingShare(subject: String?, text: String?) -> PendingShare? {
        let trimmedSubject = subject?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let trimmedText = text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedSubject.isEmpty, !trimmedText.isEmpty, trimmedSubject != trimmedText {
            return PendingShare(title: trimmedSubject, notes: trimmedText)
        }
        if !trimmedText.isEmpty {
            return PendingShare(title: trimmedText, notes: nil)
        }
        if !trimmedSubject.isEmpty {
            return PendingShare(title: trimmedSubject, notes: nil)
        }
        return nil
    }

    // MARK: - App-group queue

    private static func enqueue(_ share: PendingShare) {
        let store = UserDefaults(suiteName: appGroupSuiteName) ?? .standard
        var queue: [PendingShare] = store.data(forKey: queueKey).flatMap {
            try? JSONDecoder().decode([PendingShare].self, from: $0)
        } ?? []
        queue.append(share)
        if let data = try? JSONEncoder().encode(queue) {
            store.set(data, forKey: queueKey)
        }
    }

    // MARK: - Confirmation overlay

    /// A small self-dismissing card ("Saved to T'Day") so the share feels
    /// acknowledged; the actual task form opens in the app on next launch.
    private func showConfirmationAndFinish() {
        let card = UIView()
        card.backgroundColor = UIColor.systemBackground
        card.layer.cornerRadius = 18
        card.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: "checkmark.circle.fill"))
        icon.tintColor = UIColor.systemGreen
        icon.translatesAutoresizingMaskIntoConstraints = false

        let label = UILabel()
        label.text = NSLocalizedString("Saved — opens as a new task in T'Day", comment: "Share extension confirmation")
        label.font = UIFont.preferredFont(forTextStyle: .subheadline)
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false

        card.addSubview(icon)
        card.addSubview(label)
        view.addSubview(card)

        NSLayoutConstraint.activate([
            card.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            card.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            card.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 32),
            card.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -32),
            icon.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            icon.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 26),
            icon.heightAnchor.constraint(equalToConstant: 26),
            label.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 10),
            label.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            label.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            label.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14)
        ])

        card.alpha = 0
        UIView.animate(withDuration: 0.18) {
            card.alpha = 1
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        }
    }
}
