# Voxshelf

A free, open source, ad-free audiobook app for Android, built for the public domain recordings made by [LibriVox](https://librivox.org) volunteers.

Voxshelf is an unofficial app and is not affiliated with LibriVox. If you enjoy these audiobooks, please consider [donating to LibriVox](https://librivox.org/pages/how-to-donate/) or [volunteering as a reader](https://librivox.org/pages/volunteer-for-librivox/).

## Status

Early development. Right now this repo holds the catalog pipeline:

- `catalog/fetch_catalog.py` downloads the LibriVox catalog, one gentle request every few seconds.
- `catalog/build_catalog.py` builds the compact catalog the app keeps on the phone.
- A nightly GitHub Action runs both and publishes `catalog.db.gz` as a release.

## License

GPL-3.0. See [LICENSE](LICENSE).
