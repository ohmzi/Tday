import { prisma } from "@/lib/prisma/client";

const GLOBAL_APP_CONFIG_ID = 1;

export async function getGlobalAppConfig() {
  return prisma.appConfig.upsert({
    where: { id: GLOBAL_APP_CONFIG_ID },
    update: {},
    create: {
      id: GLOBAL_APP_CONFIG_ID,
      aiSummaryEnabled: true,
    },
  });
}

export async function setGlobalAiSummaryEnabled(params: {
  enabled: boolean;
  updatedById?: string;
}) {
  return prisma.appConfig.upsert({
    where: { id: GLOBAL_APP_CONFIG_ID },
    update: {
      aiSummaryEnabled: params.enabled,
      updatedById: params.updatedById ?? null,
    },
    create: {
      id: GLOBAL_APP_CONFIG_ID,
      aiSummaryEnabled: params.enabled,
      updatedById: params.updatedById ?? null,
    },
  });
}
