-- CreateTable
CREATE TABLE "AuthThrottle" (
    "id" TEXT NOT NULL,
    "scope" TEXT NOT NULL,
    "bucketKey" TEXT NOT NULL,
    "requestCount" INTEGER NOT NULL DEFAULT 0,
    "failureCount" INTEGER NOT NULL DEFAULT 0,
    "windowStart" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lockUntil" TIMESTAMP(3),
    "lastFailureAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AuthThrottle_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "AuthThrottle_scope_bucketKey_key" ON "AuthThrottle"("scope", "bucketKey");

-- CreateIndex
CREATE INDEX "AuthThrottle_scope_lockUntil_idx" ON "AuthThrottle"("scope", "lockUntil");
