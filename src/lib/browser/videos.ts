import type { VideoClip } from "./types";

const GTV = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample";

export const FEATURED_VIDEOS: VideoClip[] = [
  {
    id: "bbb",
    title: "Big Buck Bunny",
    channel: "Blender Foundation",
    category: "Film",
    src: `${GTV}/BigBuckBunny.mp4`,
    poster: `${GTV}/images/BigBuckBunny.jpg`,
  },
  {
    id: "sintel",
    title: "Sintel",
    channel: "Blender Foundation",
    category: "Film",
    src: `${GTV}/Sintel.mp4`,
    poster: `${GTV}/images/Sintel.jpg`,
  },
  {
    id: "tos",
    title: "Tears of Steel",
    channel: "Blender Foundation",
    category: "Film",
    src: `${GTV}/TearsOfSteel.mp4`,
    poster: `${GTV}/images/TearsOfSteel.jpg`,
  },
  {
    id: "elephants",
    title: "Elephants Dream",
    channel: "Blender Foundation",
    category: "Film",
    src: `${GTV}/ElephantsDream.mp4`,
    poster: `${GTV}/images/ElephantsDream.jpg`,
  },
  {
    id: "blazes",
    title: "For Bigger Blazes",
    channel: "Google",
    category: "Learn",
    src: `${GTV}/ForBiggerBlazes.mp4`,
    poster: `${GTV}/images/ForBiggerBlazes.jpg`,
  },
  {
    id: "joyrides",
    title: "For Bigger Joyrides",
    channel: "Google",
    category: "Learn",
    src: `${GTV}/ForBiggerJoyrides.mp4`,
    poster: `${GTV}/images/ForBiggerJoyrides.jpg`,
  },
  { id: "jfKfPfyJRdk", title: "lofi hip hop radio", channel: "Lofi Girl", category: "Music" },
  { id: "jNQXAC9IVRw", title: "Me at the zoo", channel: "jawed", category: "Classic" },
  { id: "21X5lGlDOfg", title: "NASA Live: Official stream", channel: "NASA", category: "Science" },
];

export const VIDEO_CATEGORIES = ["All", "Film", "Music", "Science", "Learn", "Classic"] as const;