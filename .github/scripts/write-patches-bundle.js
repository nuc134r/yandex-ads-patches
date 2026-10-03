// Writes patches-bundle.json, the remote patch bundle ReVanced Manager reads.
// Usage: node write-patches-bundle.js <version>
const fs = require("fs");

const version = process.argv[2];
if (!version) throw new Error("Missing version");

const repository = "nuc134r/yandex-ads-patches";

const bundle = {
  created_at: new Date().toISOString().replace(/\.\d+Z$/, ""),
  description: `Patches to remove Yandex ads, v${version}`,
  download_url: `https://github.com/${repository}/releases/download/v${version}/patches-${version}.rvp`,
  signature_download_url: null,
  version: `v${version}`,
};

fs.writeFileSync("patches-bundle.json", JSON.stringify(bundle, null, 2) + "\n");
