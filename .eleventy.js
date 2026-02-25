// .eleventy.js (ES module version)
export default function(eleventyConfig) {
  eleventyConfig.addPassthroughCopy("static");

  eleventyConfig.addGlobalData("symbols", () => import("./src/symbols.json", { with: { type: "json" } }).then(m => m.default));


  return {
    dir: {
      input: "src",
      output: "_site"
    }
  };
}
