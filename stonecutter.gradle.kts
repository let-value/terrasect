plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter active "1.21.11-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split("-", limit = 2)

    properties {
        tags(version, loader)
    }

    constants {
        match(loader, "fabric", "neoforge")
    }
}
