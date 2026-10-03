group = "app.revanced"

patches {
    about {
        name = "ReVanced Patches template"
        description = "Patches template for ReVanced"
        source = "git@github.com:revanced/revanced-patches-template.git"
        author = "ReVanced"
        contact = "contact@revanced.app"
        website = "https://revanced.app"
        license = "GNU General Public License v3.0"
    }
}

// Releases are not GPG signed. The patches plugin signs the publication with the gpg command otherwise.
tasks.withType<Sign>().configureEach {
    enabled = false
}
