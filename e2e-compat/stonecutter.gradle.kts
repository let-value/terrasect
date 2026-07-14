plugins {
  id("dev.kikugie.stonecutter")
}

stonecutter active "26.2.x"

stonecutter parameters
  {
    properties {
      tags(current.version, "fabric", "e2e-compat")
    }

    constants {
      match("fabric", "e2e-compat")
    }
  }
