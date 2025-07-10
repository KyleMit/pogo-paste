// .eleventy.js (ES module version)
export default function(eleventyConfig) {
  eleventyConfig.addPassthroughCopy("static");

  eleventyConfig.addGlobalData("symbols", () => import("./symbols.json", { assert: { type: "json" } }).then(m => m.default));


  return {
    dir: {
      input: ".",
      output: "_site"
    }
  };
}
