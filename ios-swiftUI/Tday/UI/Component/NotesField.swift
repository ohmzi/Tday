import SwiftUI
import UIKit

private enum NotesFieldMetrics {
    static let minHeight: CGFloat = 52
    // Bounded growth, not unlimited: this sheet is a fixed-height VStack with
    // no ScrollView (see the type doc below), so an unbounded field could
    // push the Schedule/Details cards below the sheet's fixed height instead
    // of just scrolling internally. Capped to roughly 2 extra lines.
    static let maxHeight: CGFloat = 88
    static let horizontalPadding: CGFloat = 18
    static let verticalPadding: CGFloat = 12
    static let eraserButtonSize: CGFloat = 32
    static let eraserIconSize: CGFloat = 18
}

// Multi-line rich-text notes field: retains bold/italic/underline/
// strikethrough pasted in from elsewhere (font size/color/family are always
// discarded — see RichNotes.swift) and downgrades pasted lists to plain
// "\u{2022} "/"1. "-prefixed lines. Selecting text and using the system edit
// menu's "Format" submenu (see Coordinator.textView(_:editMenuForTextIn:
// suggestedActions:) below) lets the user apply the same marks manually —
// there is no separate formatting toolbar, matching iOS Notes/Gmail's
// selection-menu convention. A "clear formatting" button appears only once
// real formatting is present in the field.
//
// Unlike web/Android, this sheet's form isn't inside a ScrollView (it's a
// fixed-height bottom sheet), so the field grows up to a bounded height and
// then scrolls internally rather than growing without limit.
struct NotesField: View {
    @Binding var value: String
    let placeholder: String
    // Lets a parent (e.g. CreateTaskSheet's single FocusState enum) keep this
    // field in the same focus-coordination it uses for its other inputs —
    // e.g. dismissing the keyboard when a selector sheet opens. Defaults to
    // an inert constant for standalone use.
    var isFocused: Binding<Bool> = .constant(false)

    @Environment(\.tdayColors) private var colors
    @State private var contentHeight: CGFloat = NotesFieldMetrics.minHeight

    private var hasFormatting: Bool { isRichNotes(value) }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            NotesTextViewRepresentable(
                value: $value,
                placeholder: placeholder,
                // Semibold rather than the Title field's heavy: bold marks
                // map to Nunito's heaviest weight (.black — see
                // RichNotesTextStyle), and that needs two real weight steps
                // of headroom above the base to read as "bold" at all.
                font: TdayFont.uiFont(size: 18, weight: .semibold),
                textColor: UIColor(colors.onSurface),
                placeholderColor: UIColor(colors.onSurfaceVariant.opacity(0.65)),
                contentHeight: $contentHeight,
                isFocused: isFocused
            )
            .frame(height: min(max(contentHeight, NotesFieldMetrics.minHeight), NotesFieldMetrics.maxHeight))

            if hasFormatting {
                Button {
                    value = flattenNotesToPlainText(value)
                } label: {
                    Image("LucideEraser")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: NotesFieldMetrics.eraserIconSize, height: NotesFieldMetrics.eraserIconSize)
                        .foregroundStyle(colors.onSurfaceVariant.opacity(0.7))
                        .frame(width: NotesFieldMetrics.eraserButtonSize, height: NotesFieldMetrics.eraserButtonSize)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(L("Clear formatting"))
                .padding(.top, 6)
                .padding(.trailing, 6)
            }
        }
    }
}

private struct NotesTextViewRepresentable: UIViewRepresentable {
    @Binding var value: String
    let placeholder: String
    let font: UIFont
    let textColor: UIColor
    let placeholderColor: UIColor
    @Binding var contentHeight: CGFloat
    let isFocused: Binding<Bool>

    func makeUIView(context: Context) -> RichNotesTextView {
        let style = RichNotesTextStyle(baseFont: font, baseColor: textColor)
        let textView = RichNotesTextView()
        textView.style = style
        textView.backgroundColor = .clear
        textView.font = font
        textView.textColor = textColor
        textView.isScrollEnabled = true
        textView.showsVerticalScrollIndicator = false
        textView.textContainerInset = UIEdgeInsets(
            top: NotesFieldMetrics.verticalPadding,
            left: NotesFieldMetrics.horizontalPadding,
            bottom: NotesFieldMetrics.verticalPadding,
            right: NotesFieldMetrics.horizontalPadding
        )
        textView.textContainer.lineFragmentPadding = 0
        textView.allowsEditingTextAttributes = false
        textView.tintColor = textColor
        textView.delegate = context.coordinator
        textView.attributedText = decodeNotesToAttributedString(value, style: style)
        // Pin what newly-typed text looks like: an empty attributedText (new
        // task) carries no attributes of its own, and this app's global
        // UITextView.appearance() proxy would otherwise silently override
        // the field's font once the view enters the window.
        textView.typingAttributes = [.font: font, .foregroundColor: textColor]

        let placeholderLabel = UILabel()
        placeholderLabel.text = placeholder
        placeholderLabel.font = font
        placeholderLabel.textColor = placeholderColor
        placeholderLabel.numberOfLines = 1
        placeholderLabel.translatesAutoresizingMaskIntoConstraints = false
        textView.addSubview(placeholderLabel)
        NSLayoutConstraint.activate([
            placeholderLabel.leadingAnchor.constraint(
                equalTo: textView.leadingAnchor, constant: NotesFieldMetrics.horizontalPadding
            ),
            placeholderLabel.trailingAnchor.constraint(
                lessThanOrEqualTo: textView.trailingAnchor, constant: -NotesFieldMetrics.horizontalPadding
            ),
            placeholderLabel.topAnchor.constraint(
                equalTo: textView.topAnchor, constant: NotesFieldMetrics.verticalPadding
            ),
        ])
        textView.placeholderLabel = placeholderLabel
        placeholderLabel.isHidden = !value.isEmpty

        scheduleHeightUpdate(for: textView)
        return textView
    }

    func updateUIView(_ textView: RichNotesTextView, context: Context) {
        let style = RichNotesTextStyle(baseFont: font, baseColor: textColor)
        textView.style = style
        textView.textColor = textColor
        textView.placeholderLabel?.textColor = placeholderColor
        // typingAttributes is intentionally NOT reset here on every pass —
        // this method runs on every SwiftUI update, including the one
        // immediately after a manual Format toggle, and resetting it
        // unconditionally would wipe the "continue typing in bold" state
        // that toggle just set. It's only ever set on external value
        // changes below (switching which task is open) and once in
        // makeUIView (first load).
        if value != context.coordinator.lastEmitted {
            context.coordinator.lastEmitted = value
            textView.attributedText = decodeNotesToAttributedString(value, style: style)
            let end = textView.attributedText.length
            textView.selectedRange = NSRange(location: end, length: 0)
            textView.typingAttributes = [.font: font, .foregroundColor: textColor]
        }
        textView.placeholderLabel?.isHidden = !textView.text.isEmpty
        scheduleHeightUpdate(for: textView)

        if isFocused.wrappedValue, !textView.isFirstResponder {
            textView.becomeFirstResponder()
        } else if !isFocused.wrappedValue, textView.isFirstResponder {
            textView.resignFirstResponder()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(value: $value, isFocused: isFocused)
    }

    private func scheduleHeightUpdate(for textView: UITextView) {
        DispatchQueue.main.async {
            let width = textView.bounds.width > 0
                ? textView.bounds.width
                : UIScreen.main.bounds.width - NotesFieldMetrics.horizontalPadding * 2
            let measured = textView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height
            if abs(contentHeight - measured) > 0.5 {
                contentHeight = measured
            }
        }
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        let value: Binding<String>
        let isFocused: Binding<Bool>
        var lastEmitted: String

        init(value: Binding<String>, isFocused: Binding<Bool>) {
            self.value = value
            self.isFocused = isFocused
            lastEmitted = value.wrappedValue
        }

        func textViewDidChange(_ textView: UITextView) {
            guard let richTextView = textView as? RichNotesTextView, let style = richTextView.style else { return }
            let encoded = encodeAttributedNotes(textView.attributedText, style: style)
            lastEmitted = encoded
            value.wrappedValue = encoded
            richTextView.placeholderLabel?.isHidden = !textView.text.isEmpty
        }

        func textViewDidBeginEditing(_ textView: UITextView) {
            isFocused.wrappedValue = true
        }

        func textViewDidEndEditing(_ textView: UITextView) {
            // Only clear focus if this field still owns it. UIKit's resign
            // callback can arrive after a different field has already taken
            // focus (they're on separate systems — SwiftUI's @FocusState vs
            // this UITextView's own responder chain); an unconditional clear
            // here would stomp that newer focus target back to nil.
            if isFocused.wrappedValue {
                isFocused.wrappedValue = false
            }
        }

        // Adds a "Format" submenu (Bold/Italic/Underline/Strikethrough/
        // Bulleted list/Numbered list, with checkmarks reflecting the
        // current selection) to the system's Copy/Paste/Select All popup —
        // the same place iOS Notes and Gmail put manual formatting. Only
        // offered for a real (non-empty) selection; an empty range leaves
        // the system's own menu untouched.
        func textView(
            _ textView: UITextView,
            editMenuForTextIn range: NSRange,
            suggestedActions: [UIMenuElement]
        ) -> UIMenu? {
            guard range.length > 0,
                  let richTextView = textView as? RichNotesTextView,
                  let style = richTextView.style else {
                return nil
            }
            let text = textView.attributedText ?? NSAttributedString()

            func markAction(_ mark: RichNotesMark, title: String, imageName: String) -> UIAction {
                let active = isMarkActive(mark, in: text, range: range, style: style)
                return UIAction(
                    title: title,
                    image: UIImage(named: imageName)?.withRenderingMode(.alwaysTemplate),
                    state: active ? .on : .off
                ) { [weak richTextView] _ in
                    richTextView?.applyMark(mark, in: range)
                }
            }

            func listAction(_ kind: RichNotesListKind, title: String, imageName: String) -> UIAction {
                let active = isListActive(kind, in: text, range: range)
                return UIAction(
                    title: title,
                    image: UIImage(named: imageName)?.withRenderingMode(.alwaysTemplate),
                    state: active ? .on : .off
                ) { [weak richTextView] _ in
                    richTextView?.applyList(kind, in: range)
                }
            }

            let formatMenu = UIMenu(
                title: L("Format"),
                image: UIImage(named: "LucideType")?.withRenderingMode(.alwaysTemplate),
                children: [
                    markAction(.bold, title: L("Bold"), imageName: "LucideBold"),
                    markAction(.italic, title: L("Italic"), imageName: "LucideItalic"),
                    markAction(.underline, title: L("Underline"), imageName: "LucideUnderline"),
                    markAction(.strikethrough, title: L("Strikethrough"), imageName: "LucideStrikethrough"),
                    listAction(.bullet, title: L("Bulleted list"), imageName: "LucideList"),
                    listAction(.ordered, title: L("Numbered list"), imageName: "LucideListOrdered"),
                ]
            )
            return UIMenu(children: suggestedActions + [formatMenu])
        }
    }
}

// UITextView's default paste (with `allowsEditingTextAttributes = false`)
// always coerces pasted content to plain text — there is no hook to retain
// formatting from a normal paste. This override reads the pasteboard's HTML
// representation directly (when present), sanitizes it down to the allowed
// tag set, and splices the resulting styled fragment in at the selection —
// falling back to the default plain-text paste for anything else.
final class RichNotesTextView: UITextView {
    var style: RichNotesTextStyle?
    weak var placeholderLabel: UILabel?

    override func paste(_ sender: Any?) {
        guard let style,
              let data = UIPasteboard.general.data(forPasteboardType: "public.html"),
              let html = String(data: data, encoding: .utf8) else {
            super.paste(sender)
            return
        }
        let sanitized = sanitizeHtml(html)
        let fragment = attributedFragment(fromSanitizedHtml: sanitized, style: style)
        guard fragment.length > 0 else {
            super.paste(sender)
            return
        }

        let range = selectedRange
        let mutable = NSMutableAttributedString(attributedString: attributedText)
        mutable.replaceCharacters(in: range, with: fragment)
        attributedText = mutable
        selectedRange = NSRange(location: range.location + fragment.length, length: 0)
        delegate?.textViewDidChange?(self)
    }

    // Called from the Format submenu (see Coordinator.textView(_:editMenuForTextIn:suggestedActions:)).
    func applyMark(_ mark: RichNotesMark, in range: NSRange) {
        guard let style, range.length > 0 else { return }
        let updated = togglingMark(mark, in: attributedText, range: range, style: style)
        replaceAttributedText(with: updated, selection: range)
        // Sample the LAST character of the (still-selected) range, which now
        // carries the toggled mark — sampling range.location + range.length
        // would read the character just *after* the selection instead,
        // which was never touched by this toggle.
        typingAttributes = markAttributes(at: range.location + range.length - 1, in: updated, style: style)
    }

    func applyList(_ kind: RichNotesListKind, in range: NSRange) {
        guard let style else { return }
        let (updated, selection) = togglingList(kind, in: attributedText, range: range, style: style)
        replaceAttributedText(with: updated, selection: selection)
        typingAttributes = markAttributes(at: selection.location, in: updated, style: style)
    }

    // Mutates through textStorage inside begin/endEditing and registers a
    // matching undo action, so ⌘Z / shake-to-undo work for a manual Format
    // action the same way they already do for ordinary typing (UITextView's
    // own undo support is keyed off exactly this — self.undoManager — so
    // this hooks into the same mechanism rather than inventing a new one).
    private func replaceAttributedText(with newText: NSAttributedString, selection: NSRange) {
        let previousText = NSAttributedString(attributedString: attributedText)
        let previousSelection = selectedRange

        textStorage.beginEditing()
        textStorage.replaceCharacters(in: NSRange(location: 0, length: textStorage.length), with: newText)
        textStorage.endEditing()
        selectedRange = selection

        undoManager?.setActionName(L("Format"))
        undoManager?.registerUndo(withTarget: self) { target in
            target.replaceAttributedText(with: previousText, selection: previousSelection)
        }

        delegate?.textViewDidChange?(self)
    }
}
