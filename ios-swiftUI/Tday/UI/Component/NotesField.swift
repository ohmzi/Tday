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
    static let barButtonSize: CGFloat = 34
    static let barIconSize: CGFloat = 17
}

// Multi-line rich-text notes field: retains bold/italic/underline/
// strikethrough pasted in from elsewhere (font size/color/family are always
// discarded — see RichNotes.swift) and downgrades pasted lists to plain
// "\u{2022} "/"1. "-prefixed lines. Focusing the field also shows a format
// bar below it with the same six marks/lists so they can be applied
// manually to the current selection — matching Android's format bar
// (Compose has no text-selection popup equivalent, and this keeps every
// platform's affordance in the same place instead of iOS getting a
// different pattern). A "clear formatting" button appears only once real
// formatting is present in the field.
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
    @State private var textView: RichNotesTextView?
    // UIKit owns the truth about whether this field is being edited. The
    // external `isFocused` binding can't: it's backed by a @FocusState whose
    // `.notes` case no SwiftUI view ever claims (this field is a
    // UIViewRepresentable, and only the Title TextField registers a
    // `.focused(…)` anchor), so SwiftUI resets it to nil the moment the
    // coordinator writes `.notes` into it. Reading it back as "am I focused"
    // therefore always answered false — which both hid the format bar and,
    // via updateUIView's resign branch, dismissed the keyboard on the first
    // keystroke.
    @State private var isEditing = false
    // Bumped by the Coordinator on every selection change so the format
    // bar's active-state highlighting stays current — reading
    // textView.selectedRange/.attributedText directly doesn't otherwise
    // trigger a SwiftUI re-render on its own, since neither is @State.
    @State private var selectionTick: Int = 0

    private var hasFormatting: Bool { isRichNotes(value) }

    private var style: RichNotesTextStyle {
        RichNotesTextStyle(baseFont: TdayFont.uiFont(size: 18, weight: .semibold), baseColor: UIColor(colors.onSurface))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topTrailing) {
                NotesTextViewRepresentable(
                    value: $value,
                    placeholder: placeholder,
                    // Semibold rather than the Title field's heavy: bold
                    // marks map to Nunito's heaviest weight (.black — see
                    // RichNotesTextStyle), and that needs two real weight
                    // steps of headroom above the base to read as "bold".
                    font: TdayFont.uiFont(size: 18, weight: .semibold),
                    textColor: UIColor(colors.onSurface),
                    placeholderColor: UIColor(colors.onSurfaceVariant.opacity(0.65)),
                    contentHeight: $contentHeight,
                    isFocused: isFocused,
                    isEditing: $isEditing,
                    textView: $textView,
                    selectionTick: $selectionTick
                )
                .frame(height: min(max(contentHeight, NotesFieldMetrics.minHeight), NotesFieldMetrics.maxHeight))

                if hasFormatting {
                    Button {
                        value = stripToPlainText(value)
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

            if isEditing, let textView {
                NotesFormatBar(textView: textView, style: style, tick: selectionTick)
            }
        }
    }
}

// Reads the live UITextView's selection/attributed text directly (via
// `tick`, which the parent bumps on every selection change to force this
// view to re-render and re-read them — neither is @State on its own).
private struct NotesFormatBar: View {
    let textView: RichNotesTextView
    let style: RichNotesTextStyle
    let tick: Int

    @Environment(\.tdayColors) private var colors

    private var selection: NSRange { textView.selectedRange }
    private var attributed: NSAttributedString { textView.attributedText ?? NSAttributedString() }

    // With a real selection, "active" means the whole selection carries the
    // mark. With a collapsed cursor it means the mark is armed for whatever
    // gets typed next, which lives in typingAttributes.
    private func active(_ mark: RichNotesMark) -> Bool {
        if selection.length > 0 {
            return isMarkActive(mark, in: attributed, range: selection, style: style)
        }
        return markIsActive(mark, inAttributes: textView.typingAttributes, style: style)
    }

    var body: some View {
        HStack(spacing: 2) {
            NotesFormatBarButton(
                imageName: "LucideBold",
                label: L("Bold"),
                active: active(.bold)
            ) {
                textView.applyMark(.bold)
            }
            NotesFormatBarButton(
                imageName: "LucideItalic",
                label: L("Italic"),
                active: active(.italic)
            ) {
                textView.applyMark(.italic)
            }
            NotesFormatBarButton(
                imageName: "LucideUnderline",
                label: L("Underline"),
                active: active(.underline)
            ) {
                textView.applyMark(.underline)
            }
            NotesFormatBarButton(
                imageName: "LucideStrikethrough",
                label: L("Strikethrough"),
                active: active(.strikethrough)
            ) {
                textView.applyMark(.strikethrough)
            }

            Rectangle()
                .fill(colors.onSurfaceVariant.opacity(0.2))
                .frame(width: 1, height: 20)
                .padding(.horizontal, 2)

            NotesFormatBarButton(
                imageName: "LucideList",
                label: L("Bulleted list"),
                active: isListActive(.bullet, in: attributed, range: selection)
            ) {
                textView.applyList(.bullet)
            }
            NotesFormatBarButton(
                imageName: "LucideListOrdered",
                label: L("Numbered list"),
                active: isListActive(.ordered, in: attributed, range: selection)
            ) {
                textView.applyList(.ordered)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 2)
    }
}

private struct NotesFormatBarButton: View {
    let imageName: String
    let label: String
    let active: Bool
    let action: () -> Void

    @Environment(\.tdayColors) private var colors

    var body: some View {
        Button(action: action) {
            Image(imageName)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: NotesFieldMetrics.barIconSize, height: NotesFieldMetrics.barIconSize)
                .foregroundStyle(active ? colors.primary : colors.onSurfaceVariant.opacity(0.75))
                .frame(width: NotesFieldMetrics.barButtonSize, height: NotesFieldMetrics.barButtonSize)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(label)
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
    @Binding var isEditing: Bool
    @Binding var textView: RichNotesTextView?
    @Binding var selectionTick: Int

    func makeUIView(context: Context) -> RichNotesTextView {
        let style = RichNotesTextStyle(baseFont: font, baseColor: textColor)
        let newTextView = RichNotesTextView()
        newTextView.style = style
        newTextView.backgroundColor = .clear
        newTextView.font = font
        newTextView.textColor = textColor
        newTextView.isScrollEnabled = true
        newTextView.showsVerticalScrollIndicator = false
        newTextView.textContainerInset = UIEdgeInsets(
            top: NotesFieldMetrics.verticalPadding,
            left: NotesFieldMetrics.horizontalPadding,
            bottom: NotesFieldMetrics.verticalPadding,
            right: NotesFieldMetrics.horizontalPadding
        )
        newTextView.textContainer.lineFragmentPadding = 0
        newTextView.allowsEditingTextAttributes = false
        newTextView.tintColor = textColor
        newTextView.delegate = context.coordinator
        newTextView.attributedText = decodeNotesToAttributedString(value, style: style)
        // Pin what newly-typed text looks like: an empty attributedText (new
        // task) carries no attributes of its own, and this app's global
        // UITextView.appearance() proxy would otherwise silently override
        // the field's font once the view enters the window.
        newTextView.typingAttributes = [.font: font, .foregroundColor: textColor]

        let placeholderLabel = UILabel()
        placeholderLabel.text = placeholder
        placeholderLabel.font = font
        placeholderLabel.textColor = placeholderColor
        placeholderLabel.numberOfLines = 1
        placeholderLabel.translatesAutoresizingMaskIntoConstraints = false
        newTextView.addSubview(placeholderLabel)
        NSLayoutConstraint.activate([
            placeholderLabel.leadingAnchor.constraint(
                equalTo: newTextView.leadingAnchor, constant: NotesFieldMetrics.horizontalPadding
            ),
            placeholderLabel.trailingAnchor.constraint(
                lessThanOrEqualTo: newTextView.trailingAnchor, constant: -NotesFieldMetrics.horizontalPadding
            ),
            placeholderLabel.topAnchor.constraint(
                equalTo: newTextView.topAnchor, constant: NotesFieldMetrics.verticalPadding
            ),
        ])
        newTextView.placeholderLabel = placeholderLabel
        placeholderLabel.isHidden = !value.isEmpty

        scheduleHeightUpdate(for: newTextView)
        DispatchQueue.main.async {
            textView = newTextView
        }
        return newTextView
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

        // Focus-in only. There is deliberately no resign branch here: the
        // `isFocused` binding reads back false even while this field is
        // being edited (see the isEditing note on NotesField), so any
        // "binding says false → resign" rule fires on the first keystroke
        // and dismisses the keyboard. Dismissal is driven from the other
        // direction instead — CreateTaskSheet broadcasts a resignFirstResponder
        // when it opens a selector, which reaches this text view without
        // needing @FocusState to model it.
        if isFocused.wrappedValue, !textView.isFirstResponder {
            textView.becomeFirstResponder()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(value: $value, isFocused: isFocused, isEditing: $isEditing, selectionTick: $selectionTick)
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
        let isEditing: Binding<Bool>
        let selectionTick: Binding<Int>
        var lastEmitted: String

        init(
            value: Binding<String>,
            isFocused: Binding<Bool>,
            isEditing: Binding<Bool>,
            selectionTick: Binding<Int>
        ) {
            self.value = value
            self.isFocused = isFocused
            self.isEditing = isEditing
            self.selectionTick = selectionTick
            lastEmitted = value.wrappedValue
        }

        func textViewDidChange(_ textView: UITextView) {
            guard let richTextView = textView as? RichNotesTextView, let style = richTextView.style else { return }
            let encoded = encodeAttributedNotes(textView.attributedText, style: style)
            lastEmitted = encoded
            value.wrappedValue = encoded
            richTextView.placeholderLabel?.isHidden = !textView.text.isEmpty
        }

        func textViewDidChangeSelection(_ textView: UITextView) {
            selectionTick.wrappedValue += 1
        }

        func textView(
            _ textView: UITextView,
            shouldChangeTextIn range: NSRange,
            replacementText text: String
        ) -> Bool {
            guard text == "\n", let richTextView = textView as? RichNotesTextView else { return true }
            return !richTextView.continueListIfNeeded(at: range)
        }

        func textViewDidBeginEditing(_ textView: UITextView) {
            isEditing.wrappedValue = true
            // Still written even though it reads back false immediately: this
            // is what blurs the Title TextField, which *does* claim its
            // @FocusState case.
            isFocused.wrappedValue = true
        }

        func textViewDidEndEditing(_ textView: UITextView) {
            isEditing.wrappedValue = false
            // Only clear focus if this field still owns it. UIKit's resign
            // callback can arrive after a different field has already taken
            // focus (they're on separate systems — SwiftUI's @FocusState vs
            // this UITextView's own responder chain); an unconditional clear
            // here would stomp that newer focus target back to nil.
            if isFocused.wrappedValue {
                isFocused.wrappedValue = false
            }
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

    // Called from the format bar (see NotesFormatBar in this file).
    func applyMark(_ mark: RichNotesMark) {
        guard let style else { return }
        let range = selectedRange
        // Collapsed cursor: nothing to restyle, so arm the mark for whatever
        // gets typed next instead of no-oping. This is what makes "tap Bold,
        // then start typing" work the way it does in Notes/Gmail/Docs.
        guard range.length > 0 else {
            typingAttributes = togglingMarkInTypingAttributes(mark, in: typingAttributes, style: style)
            return
        }
        let updated = togglingMark(mark, in: attributedText, range: range, style: style)
        replaceAttributedText(with: updated, selection: range)
        // Sample the LAST character of the (still-selected) range, which now
        // carries the toggled mark — sampling range.location + range.length
        // would read the character just *after* the selection instead,
        // which was never touched by this toggle.
        typingAttributes = markAttributes(at: range.location + range.length - 1, in: updated, style: style)
    }

    // Enter on a list line: continue the list instead of dropping a bare,
    // untagged newline that silently ends it. Returns whether it handled the
    // keystroke, so the delegate knows to suppress the default insertion.
    func continueListIfNeeded(at range: NSRange) -> Bool {
        guard let style, range.length == 0,
              let (updated, selection) = continuingListOnNewline(attributedText, cursor: range.location, style: style)
        else { return false }
        replaceAttributedText(with: updated, selection: selection)
        typingAttributes = markAttributes(at: selection.location, in: updated, style: style)
        return true
    }

    // No collapsed-cursor special case: a list is a line-level format, so
    // togglingList works off the cursor's whole line via lineRange(for:)
    // whether or not anything is selected.
    func applyList(_ kind: RichNotesListKind) {
        guard let style else { return }
        let (updated, selection) = togglingList(kind, in: attributedText, range: selectedRange, style: style)
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
