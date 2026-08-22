import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { AttachmentTaskType, TaskAttachmentType } from "@/types";

/** Attachments hang off both feeds, so every cache key is scoped by type *and* id. */
export const attachmentsQueryKey = (
  taskType: AttachmentTaskType,
  taskId: string,
) => ["attachments", taskType, taskId] as const;

const basePath = (taskType: AttachmentTaskType, taskId: string) =>
  taskType === "todo"
    ? `/api/todo/${taskId}/attachments`
    : `/api/floater/${taskId}/attachments`;

/** Source URLs for the bytes. Kept here so nothing else has to know the route shape. */
export const attachmentImageUrl = (id: string) => `/api/attachment/${id}`;
export const attachmentThumbnailUrl = (id: string) =>
  `/api/attachment/${id}/thumbnail`;

/**
 * Loads a task's pictures. Like task steps, these are fetched on demand inside the
 * editor rather than riding along with the list payload, so `enabled` gates the
 * request until the editor is actually open.
 */
export function useAttachments(
  taskType: AttachmentTaskType,
  taskId: string,
  enabled: boolean,
) {
  return useQuery<TaskAttachmentType[]>({
    queryKey: attachmentsQueryKey(taskType, taskId),
    queryFn: async () => {
      const data = (await api.GET({ url: basePath(taskType, taskId) })) as {
        attachments: TaskAttachmentType[];
      };
      return data.attachments ?? [];
    },
    enabled,
  });
}

/**
 * Uploads one picture as multipart form data.
 *
 * No `Content-Type` header is set on purpose: the browser has to generate it so the
 * multipart boundary matches the body it actually writes. Setting it by hand produces
 * a boundary-less header and the server cannot parse the request.
 */
export function useUploadAttachment() {
  const queryClient = useQueryClient();

  return useMutation<
    TaskAttachmentType | null,
    Error,
    { taskType: AttachmentTaskType; taskId: string; file: File }
  >({
    mutationFn: async ({ taskType, taskId, file }) => {
      const body = new FormData();
      body.append("file", file);
      const data = (await api.POST({
        url: basePath(taskType, taskId),
        body,
      })) as { attachment?: TaskAttachmentType } | null;
      return data?.attachment ?? null;
    },
    onSettled: (_data, _error, { taskType, taskId }) => {
      queryClient.invalidateQueries({
        queryKey: attachmentsQueryKey(taskType, taskId),
      });
    },
  });
}

export function useDeleteAttachment() {
  const queryClient = useQueryClient();

  return useMutation<
    void,
    Error,
    { id: string; taskType: AttachmentTaskType; taskId: string }
  >({
    mutationFn: async ({ id }) => {
      await api.DELETE({ url: `/api/attachment/${id}` });
    },
    onSettled: (_data, _error, { taskType, taskId }) => {
      queryClient.invalidateQueries({
        queryKey: attachmentsQueryKey(taskType, taskId),
      });
    },
  });
}
