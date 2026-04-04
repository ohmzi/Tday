const UTC_OFFSET_SUFFIX_PATTERN = /(Z|[+-]\d{2}:\d{2})$/i;
const UTC_LIKE_DATE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(\.\d{1,9})?)?$/;

function parseUtcLikeDateTime(value: string): Date | null {
  const match = UTC_LIKE_DATE_TIME_PATTERN.exec(value);
  if (!match) {
    return null;
  }

  const [
    ,
    year,
    month,
    day,
    hours,
    minutes,
    seconds = "0",
    fraction = "",
  ] = match;
  const milliseconds = fraction
    ? Number(fraction.slice(1).padEnd(3, "0").slice(0, 3))
    : 0;

  return new Date(
    Date.UTC(
      Number(year),
      Number(month) - 1,
      Number(day),
      Number(hours),
      Number(minutes),
      Number(seconds),
      milliseconds,
    ),
  );
}

export default function parseApiDateTime(value: string | Date): Date {
  if (value instanceof Date) {
    return new Date(value);
  }

  if (!UTC_OFFSET_SUFFIX_PATTERN.test(value)) {
    const parsedUtcLikeDateTime = parseUtcLikeDateTime(value);
    if (parsedUtcLikeDateTime) {
      return parsedUtcLikeDateTime;
    }
  }

  return new Date(value);
}
