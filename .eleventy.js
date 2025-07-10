// .eleventy.js (ES module version)
export default function(eleventyConfig) {
  eleventyConfig.addPassthroughCopy("style.css");
  eleventyConfig.addPassthroughCopy("script.js");

eleventyConfig.addGlobalData("symbols", async () => {
  const data = await import("./symbols.json", { assert: { type: "json" } });
  return data.default;
});
  return {
    dir: {
      input: ".",
      output: "_site"
    }
  };
}
