# Hints for humans (not shown to the agent)

- `Calendar.getInstance()` and `SimpleDateFormat` use the JVM's default time zone, so the
  same instant gives different answers on different servers.
- `SimpleDateFormat` is lenient by default, and it is not thread safe. One shared
  instance used by several threads returns garbage.
- `YYYY` is the *week* year: for the last days of December it prints next year.
- Dividing milliseconds by 86 400 000 assumes every day has 24 hours. Two days a year
  do not.
- `Calendar.add(Calendar.MONTH, 1)` on 28 February gives 28 March, not 31 March.
- Someone forgot a holiday in `HolidayTable`.
- `java.time` and its strict `LocalDate.parse` remove most of this by design.
