-- Add server-side revocation version to user sessions.
ALTER TABLE "User"
ADD COLUMN "tokenVersion" INTEGER NOT NULL DEFAULT 0;

-- Track successful credential login signals for anomaly detection.
CREATE TABLE "AuthSignal" (
    "id" TEXT NOT NULL,
    "identifierHash" TEXT NOT NULL,
    "lastIpHash" TEXT,
    "lastDeviceHash" TEXT,
    "lastSeenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AuthSignal_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "AuthSignal_identifierHash_key" ON "AuthSignal"("identifierHash");
CREATE INDEX "AuthSignal_lastSeenAt_idx" ON "AuthSignal"("lastSeenAt");
