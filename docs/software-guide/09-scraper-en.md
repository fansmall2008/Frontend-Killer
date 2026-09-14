# Scraper Systems

> Related pages: `scraper-system-list.html`, `scraper-system-edit.html`

The Scraper Systems page manages the list of game consoles, handhelds, and arcade systems supported by ScreenScraper, providing the system mapping foundation for data scraping.

---

## System List Page

> Page file: `scraper-system-list.html`

### Page Header

| Element | Description |
|---------|-------------|
| **"Back to Home"** button | Returns to the home page |

### Toolbar

| Button | Description |
|--------|-------------|
| **"Add System"** | Open the system edit page to add a new system |
| **"Initialize Systems"** | Pull all system data from the ScreenScraper API and import |
| **"Clear All"** | Delete all configured system records |

### System Table

| Column | Description |
|--------|-------------|
| **ID** | Database record ID |
| **System ID** | System number assigned by ScreenScraper |
| **Name** | System name (French original) |
| **English Name** | System English name |
| **Manufacturer** | System manufacturer (e.g., Nintendo, Sony, Sega) |
| **Type** | System type (e.g., console, arcade, handheld) |
| **Release Year** | System release year |
| **Media Status** | Availability status of each media type for this system |

### Row Actions

| Button | Description |
|--------|-------------|
| **"Edit"** | Enter the system edit page to modify this system |
| **"Scrape"** | Execute media scraping for all games under this system |
| **"Delete"** | Delete this system record |

### Scrape Modal

Opens when clicking the "Scrape" button, confirming media scraping for all games in this system:

| Element | Description |
|---------|-------------|
| **Confirmation info** | Shows the system name and media scope to be scraped |
| **"Confirm"** button | Start the scraping task |
| **"Cancel"** button | Close the modal |

### Pagination

Displays 20 records per page.

---

## System Edit Page

> Page file: `scraper-system-edit.html`

Used to add or edit scraper system information.

### Page Header

| Element | Description |
|---------|-------------|
| **"← Back to System List"** link | Returns to the system list page |
| **Page title** | Shows "Add Scraper System" when adding, "Edit Scraper System" when editing |

### Form Fields

| Field | Required | Description |
|-------|----------|-------------|
| **System ID (systemId)** | ✅ | System number assigned by ScreenScraper |
| **Type (type)** | | System type, e.g., console, arcade, handheld |
| **Name (name)** | ✅ | System name (French original) |

### Multilingual Names

| Field | Description |
|-------|-------------|
| **English Name (nameEn)** | System English name |
| **French Name (nameFr)** | System French name |
| **Japanese Name (nameJp)** | System Japanese name |
| **Chinese Name (nameCn)** | System Chinese name |

### Other Fields

| Field | Description |
|-------|-------------|
| **Manufacturer (company)** | System manufacturer, e.g., Nintendo, Sony, Sega |
| **Release Year (releaseYear)** | System release year |
| **Icon URL (iconUrl)** | System icon link |

### Action Buttons

| Button | Description |
|--------|-------------|
| **"Cancel"** | Return to the system list |
| **"Save"** | Save system information (automatically returns to the list after saving) |

---

## Next Steps

- Configure scraper credentials → [System Settings](11-system-settings-en.md)
- View media download progress → [Media Download](10-media-download-en.md)
