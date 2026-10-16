"use client";

import { useSyncExternalStore } from "react";
import {
  getServerStoreSnapshot,
  getStoreSnapshot,
  subscribeToPortalData,
} from "@/lib/data";
import type { PortalStore } from "@/lib/types";

export function usePortalData(): PortalStore {
  return useSyncExternalStore<PortalStore>(
    subscribeToPortalData,
    getStoreSnapshot,
    getServerStoreSnapshot,
  );
}
