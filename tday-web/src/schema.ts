import { z } from "zod";

export const todoSchema = z.object({
  title: z
    .string({ message: "title cannot be left empty" })
    .trim()
    .min(1, { message: "title cannot be left empty" }),
  // Tasks without a description carry `null` (see TodoItemType). Allowing only
  // string | undefined caused reschedule/drag payloads built from a raw todo to
  // fail validation, so `patchTodo` silently skipped the PATCH (the move never
  // saved). The backend ignores a null description, leaving it unchanged.
  description: z.string().nullable().optional(),
  priority: z.enum(["Lowest", "Low", "Medium", "High"], {
    errorMap: () => ({ message: "priority must be one of: lowest, low, medium, high" }),
  }),
  due: z
    .date({ message: "end date is not identified" })
    .transform((d) => {
      const x = new Date(d);
      x.setSeconds(0, 0);
      return x;
    }),
  rrule: z.string().nullable(),
  listID: z.string().nullable().optional(),
});

export const todoInstanceSchema = z.object({
  title: z
    .string({ message: "title cannot be left empty" })
    .trim()
    .min(1, { message: "title cannot be left empty" }),
  // See todoSchema: a null description must validate so recurring-instance
  // reschedules from a raw todo aren't silently dropped.
  description: z.string().nullable().optional(),
  priority: z.enum(["Lowest", "Low", "Medium", "High"], {
    errorMap: () => ({ message: "priority must be one of: lowest, low, medium, high" }),
  }),
  due: z
    .date({ message: "end date is not identified" })
    .transform((d) => {
      const x = new Date(d);
      x.setSeconds(0, 0);
      return x;
    }),
  instanceDate: z.date({ message: "instance date is not identified" }),
  rrule: z.string().nullable(),
});

export const floaterSchema = z.object({
  title: z
    .string({ message: "title cannot be left empty" })
    .trim()
    .min(1, { message: "title cannot be left empty" }),
  description: z.string().nullable().optional(),
  priority: z.enum(["Lowest", "Low", "Medium", "High"], {
    errorMap: () => ({ message: "priority must be one of: lowest, low, medium, high" }),
  }),
  listID: z.string().nullable().optional(),
});

const listColorValues = [
  "RED",
  "ORANGE",
  "YELLOW",
  "LIME",
  "BLUE",
  "PURPLE",
  "PINK",
  "TEAL",
  "CORAL",
  "GOLD",
  "DEEP_BLUE",
  "ROSE",
  "LIGHT_RED",
  "BRICK",
  "SLATE",
] as const;
const listBaseSchema = z.object({
  id: z.string({ message: "id cannot be left empty" }),
  name: z
    .string({ message: "title cannot be left empty" })
    .trim()
    .min(1, { message: "title cannot be left empty" }),
  color: z.enum(listColorValues).nullable(),
  iconKey: z.string().trim().min(1).nullable().optional(),
});

export const listCreateSchema = listBaseSchema.pick({
  name: true,
}).extend({
  color: z.enum(listColorValues).optional(),
  iconKey: z.string().trim().min(1).max(64).optional(),
  reusable: z.boolean().optional(),
  defaultPriority: z.enum(["Lowest", "Low", "Medium", "High"]).nullable().optional(),
});
