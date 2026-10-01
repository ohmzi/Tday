import { z } from "zod";
export const registrationSchema = z.object({
  fname: z
    .string({ error: "name cannot be left empty" })
    .trim()
    .min(2, { error: "first name is atleast two characters" }),
  lname: z.string().optional(),
  username: z
    .string({ error: "username cannot be left empty" })
    .trim()
    .toLowerCase()
    .regex(/^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])$/, {
      error: "username is incorrect",
    }),
  password: z
    .string({ error: "password cannot be empty" })
    .min(8, { error: "password cannot be smaller than 8" })
    .regex(/[A-Z]/, {
      error: "password must have at least one uppercase letter",
    })
    .regex(/[\W_]/, {
      error: "password must have at least one special character",
    }),
});

export const todoSchema = z.object({
  title: z
    .string({ error: "title cannot be left empty" })
    .trim()
    .min(1, { error: "title cannot be left empty" }),
  // Tasks without a description carry `null` (see TodoItemType). Allowing only
  // string | undefined caused reschedule/drag payloads built from a raw todo to
  // fail validation, so `patchTodo` silently skipped the PATCH (the move never
  // saved). The backend ignores a null description, leaving it unchanged.
  description: z.string().nullable().optional(),
  priority: z.enum(["Lowest", "Low", "Medium", "High"], {
    error: "priority must be one of: lowest, low, medium, high",
  }),
  due: z
    .date({ error: "end date is not identified" })
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
    .string({ error: "title cannot be left empty" })
    .trim()
    .min(1, { error: "title cannot be left empty" }),
  // See todoSchema: a null description must validate so recurring-instance
  // reschedules from a raw todo aren't silently dropped.
  description: z.string().nullable().optional(),
  priority: z.enum(["Lowest", "Low", "Medium", "High"], {
    error: "priority must be one of: lowest, low, medium, high",
  }),
  due: z
    .date({ error: "end date is not identified" })
    .transform((d) => {
      const x = new Date(d);
      x.setSeconds(0, 0);
      return x;
    }),
  instanceDate: z.date({ error: "instance date is not identified" }),
  rrule: z.string().nullable(),
});

export const floaterSchema = z.object({
  title: z
    .string({ error: "title cannot be left empty" })
    .trim()
    .min(1, { error: "title cannot be left empty" }),
  description: z.string().nullable().optional(),
  priority: z.enum(["Lowest", "Low", "Medium", "High"], {
    error: "priority must be one of: lowest, low, medium, high",
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
  id: z.string({ error: "id cannot be left empty" }),
  name: z
    .string({ error: "title cannot be left empty" })
    .trim()
    .min(1, { error: "title cannot be left empty" }),
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

export type ListColorType = (typeof listColorValues)[number];

export const listPatchSchema = listBaseSchema.partial().extend({
  name: z
    .string({ error: "title cannot be left empty" })
    .trim()
    .min(1, { error: "title cannot be left empty" })
    .optional(),
  color: z.enum(listColorValues).optional(),
  iconKey: z.string().trim().min(1).max(64).optional(),
});

export const userPreferencesSchema = z.object({
  sortBy: z.enum(["due", "priority"]).nullable().optional(),
  groupBy: z.enum(["due", "priority", "rrule", "list"]).nullable().optional(),
  direction: z.enum(["Ascending", "Descending"]).nullable().optional(),
});
