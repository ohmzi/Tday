import Foundation
import CryptoKit

struct ProbeCompatibilityPayload: Codable, Equatable {
    let appVersion: String
    let updateRequired: Bool
}

enum ProbeDecryptor {
    private static let ivLength = 12

    static func decrypt(_ encryptedBase64URL: String) -> ProbeCompatibilityPayload? {
        guard let keyString = Bundle.main.object(forInfoDictionaryKey: "TdayProbeEncryptionKey") as? String,
              !keyString.isEmpty,
              !keyString.hasPrefix("$(") else {
            return nil
        }

        guard let keyData = base64URLDecode(keyString),
              keyData.count == 32,
              let blob = base64URLDecode(encryptedBase64URL),
              blob.count > ivLength else {
            return nil
        }

        do {
            let key = SymmetricKey(data: keyData)
            let sealedBox = try AES.GCM.SealedBox(combined: blob)
            let plaintext = try AES.GCM.open(sealedBox, using: key)
            return try JSONDecoder().decode(ProbeCompatibilityPayload.self, from: plaintext)
        } catch {
            return nil
        }
    }

    private static func base64URLDecode(_ input: String) -> Data? {
        var base64 = input
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64.append(String(repeating: "=", count: 4 - remainder))
        }
        return Data(base64Encoded: base64)
    }
}
