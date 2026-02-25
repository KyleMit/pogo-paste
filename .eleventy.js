// .eleventy.js (ES module version)
export default function(eleventyConfig) {
  eleventyConfig.addPassthroughCopy("src/static");

  return {
    dir: {
      input: "src",
      output: "_site"
    }
  };
}
