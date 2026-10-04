group = "io.github.nuc134r"

patches {
    about {
        name = "Yandex Ads Patches"
        description = "Patches to remove ads from Yandex apps"
        source = "git@github.com:nuc134r/yandex-ads-patches.git"
        author = "nuc134r"
        contact = "https://github.com/nuc134r/yandex-ads-patches/issues"
        website = "https://github.com/nuc134r/yandex-ads-patches"
        license = "GNU General Public License v3.0"
    }
}

// Releases are not GPG signed. The patches plugin signs the publication with the gpg command otherwise.
tasks.withType<Sign>().configureEach {
    enabled = false
}
