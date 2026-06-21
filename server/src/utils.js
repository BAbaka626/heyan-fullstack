function normalizeText(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function splitIntoChunks(text, maxLength = 380) {
  const clean = normalizeText(text);
  if (!clean) {
    return [];
  }

  const sections = clean.split(/(?<=[。！？；\n])/);
  const chunks = [];
  let current = "";

  for (const section of sections) {
    if (!section) {
      continue;
    }

    if ((current + section).length > maxLength && current) {
      chunks.push(current.trim());
      current = section;
    } else {
      current += section;
    }
  }

  if (current.trim()) {
    chunks.push(current.trim());
  }

  return chunks;
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function toNumber(value, fallback = 0) {
  const num = Number(value);
  return Number.isFinite(num) ? num : fallback;
}

module.exports = {
  clamp,
  normalizeText,
  splitIntoChunks,
  toNumber,
};
