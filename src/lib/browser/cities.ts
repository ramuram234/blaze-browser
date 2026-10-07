export const CITIES: Record<string, { lat: number; lon: number; label: string }> = {
  delhi: { lat: 28.6139, lon: 77.209, label: "Delhi" },
  mumbai: { lat: 19.076, lon: 72.8777, label: "Mumbai" },
  bengaluru: { lat: 12.9716, lon: 77.5946, label: "Bengaluru" },
  london: { lat: 51.5074, lon: -0.1278, label: "London" },
  nyc: { lat: 40.7128, lon: -74.006, label: "New York" },
  tokyo: { lat: 35.6762, lon: 139.6503, label: "Tokyo" },
  singapore: { lat: 1.3521, lon: 103.8198, label: "Singapore" },
};

export const CITY_KEYS = Object.keys(CITIES);
