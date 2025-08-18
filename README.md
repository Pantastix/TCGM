# TCGM - Trading Card Game Manager
A powerful, multiplatform tool for Pokémon card collectors and traders to digitize, structure, and manage their entire card inventory. Keep track of what you own and what it's worth.

## About The Project
TCGM is designed for both passionate collectors and serious traders of Pokémon cards. Its primary goal is to provide a seamless experience for digitizing and organizing your physical card collection. With TCGM, you can always have a clear overview of which cards you own, how many copies you have, and their current market value, all powered by up-to-date data from the TCGdex API.

Whether you're managing your collection locally or syncing it to your own private cloud database with Supabase, TCGM provides the flexibility you need.

## Key Features
- **Effortless Card Management:** Easily add, edit, and manage cards in your digital inventory.
- **Always Up-to-Date:** Card and set information is kept current thanks to the live TCGdex API.
- **Flexible Data Storage:**
  - Store your collection locally on your device.
  - Connect to your own free Supabase instance for cloud synchronization across multiple devices.
- **Direct Cardmarket Integration:** Automatically generates Cardmarket links for quick access to product pages.
- **Multi-Language Support:** Full support for English and German (with more languages in progress).
- **Auto-Updater (Desktop):** The desktop application automatically checks for new versions on startup and offers a seamless update process.

## Supported Platforms
You can download and use TCGM on the following platforms:
- Desktop (Windows .msi, Linux .deb, macOS .dmg)
- Android (.apk)

[![Github All Releases](https://img.shields.io/github/downloads/Pantastix/TCGM/total.svg)]()

Find the latest installers on our [GitHub Releases Page](https://www.google.com/search?q=https://github.com/Pantastix/TCGM/releases).

## Roadmap
Here are some of the features planned for future releases:
- 📊 Valuation Tool: A dedicated screen to calculate and visualize the total value of your collection.
- 📄 Export Functionality: Export your collection data to CSV and PDF formats for easy sharing and archiving.

## Building From Source (For Developers)
Getting the project up and running locally is straightforward.

### Prerequisites
- JDK 21: Ensure you have the Java Development Kit version 21 installed.
### Setup
1. Clone the repository:
```
git clone https://github.com/Pantastix/TCGM.git
```
2. Open the project in IntelliJ IDEA.
3. Let Gradle sync the dependencies.
4. Run the `desktopRun` Gradle task to start the desktop application.

## License
This project is currently proprietary. Please check back later for updates on licensing.

## Acknowledgments
A special thanks to the team behind the TCGdex API for providing the comprehensive and reliable data that powers this application.
