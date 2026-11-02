# R8 rules for the release build.
# Hilt, Room, DataStore, Compose and Navigation ship their own consumer rules, so nothing
# is needed here for them. Stored values never rely on class or enum names (priority is
# stored as a number, reminders and themes as fixed string keys), so obfuscation is safe.
