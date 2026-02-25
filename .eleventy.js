// .eleventy.js (ES module version)
export default function(eleventyConfig) {
  eleventyConfig.addPassthroughCopy("static");

  return {
    dir: {
      input: "src",
      output: "_site"
    }
  };
}
