import CryptoKit
import Foundation
import Security

struct CredentialEnvelope {
    let encryptedPayload: String
    let encryptedKey: String
    let encryptedIv: String
    let keyId: String
    let version: String
}

private struct CredentialEnvelopePayload: Codable {
    let email: String
    let password: String
}

enum CredentialEnvelopeBuilder {
    private static let supportedVersion = "1"

    static func build(email: String, password: String, credentialKey: CredentialKeyResponse) throws -> CredentialEnvelope {
        guard credentialKey.version == supportedVersion else {
            throw APIError(message: "Unsupported secure sign-in version", statusCode: nil)
        }

        let publicKeyData = try Data(base64URLEncoded: credentialKey.publicKey)
        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeyClass as String: kSecAttrKeyClassPublic,
            kSecAttrKeySizeInBits as String: 2048,
        ]

        var keyError: Unmanaged<CFError>?
        guard let publicKey = SecKeyCreateWithData(publicKeyData as CFData, attributes as CFDictionary, &keyError) else {
            throw APIError(message: "Could not initialize secure sign-in", statusCode: nil)
        }

        let symmetricKey = SymmetricKey(size: .bits256)
        let nonceData = Data((0 ..< 12).map { _ in UInt8.random(in: 0 ... 255) })
        let nonce = try AES.GCM.Nonce(data: nonceData)
        let payload = try JSONEncoder().encode(
            CredentialEnvelopePayload(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
                password: password
            )
        )

        let sealedBox = try AES.GCM.seal(payload, using: symmetricKey, nonce: nonce)
        let encryptedPayload = sealedBox.ciphertext + sealedBox.tag
        let symmetricKeyData = symmetricKey.withUnsafeBytes { Data($0) }

        var encryptionError: Unmanaged<CFError>?
        guard let encryptedKeyData = SecKeyCreateEncryptedData(
            publicKey,
            .rsaEncryptionOAEPSHA256,
            symmetricKeyData as CFData,
            &encryptionError
        ) as Data?
        else {
            throw APIError(message: "Could not prepare secure sign-in flow", statusCode: nil)
        }

        return CredentialEnvelope(
            encryptedPayload: encryptedPayload.base64URLEncodedString(),
            encryptedKey: encryptedKeyData.base64URLEncodedString(),
            encryptedIv: nonceData.base64URLEncodedString(),
            keyId: credentialKey.keyId,
            version: credentialKey.version
        )
    }
}

private extension Data {
    init(base64URLEncoded value: String) throws {
        let base64 = value
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padded = base64.padding(toLength: ((base64.count + 3) / 4) * 4, withPad: "=", startingAt: 0)
        guard let data = Data(base64Encoded: padded) else {
            throw APIError(message: "Could not decode secure sign-in key", statusCode: nil)
        }
        self = data
    }

    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
