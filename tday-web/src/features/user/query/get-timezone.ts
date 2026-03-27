import { useQueryClient } from "@tanstack/react-query";
import resolveTimezone from "@/lib/date/resolveTimezone";

export const useUserTimezone = () => {
  const queryClient = useQueryClient();
  const cachedTimeZone = queryClient.getQueryData<{ timeZone: string }>([
    "userTimezone",
  ]);
  return {
    timeZone: resolveTimezone(cachedTimeZone?.timeZone),
  };
};
