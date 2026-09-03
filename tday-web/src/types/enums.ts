export enum SortBy {
  due = "due",
  priority = "priority",
}

export enum GroupBy {
  due = "due",
  priority = "priority",
  rrule = "rrule",
  list = "list",
}

export enum Direction {
  Ascending = "Ascending",
  Descending = "Descending",
}

/** Which root feed opens on a fresh cold launch. */
export enum DefaultHomeScreen {
  scheduled = "scheduled",
  floater = "floater",
}

export enum UserRole {
  USER = "USER",
  ADMIN = "ADMIN",
}

export enum ApprovalStatus {
  PENDING = "PENDING",
  APPROVED = "APPROVED",
}
