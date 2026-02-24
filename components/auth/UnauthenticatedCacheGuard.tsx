"use client";

import { useEffect } from "react";
import { clearClientUserData } from "@/lib/security/clearClientUserData";

export default function UnauthenticatedCacheGuard() {
  useEffect(() => {
    void clearClientUserData();
  }, []);

  return null;
}
