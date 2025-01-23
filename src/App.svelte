<script lang="ts">

  let types = [
    { name: "Bug", symbol: "ഋ", alternates: "⁂ප" },
    { name: "Dark", symbol: "☪", alternates: "☽" },
    { name: "Dragon", symbol: "☬", alternates: "中" },
    { name: "Electric", symbol: "ϟ", alternates: "☇☈⚡" },
    { name: "Fairy", symbol: "✦", alternates: "❤" },
    { name: "Fighting", symbol: "夫", alternates: "⛩☯" },
    { name: "Fire", symbol: "♨", alternates: "㊋" },
    { name: "Flying", symbol: "༄", alternates: "࿓✈⊱⊰" },
    { name: "Ghost", symbol: "⚉", alternates: "ᗝ" },
    { name: "Grass", symbol: "⸙", alternates: "㊍☘⚘" },
    { name: "Ground", symbol: "ᨏ", alternates: "㊏⏚" },
    { name: "Ice", symbol: "❅", alternates: "☃❄" },
    { name: "Normal", symbol: "⦿", alternates: "✴" },
    { name: "Poison", symbol: "☠", alternates: "☣" },
    { name: "Psychic", symbol: "꩜", alternates: "ത⛧⛤❂ᯣ۞⚙᳀" },
    { name: "Rock", symbol: "☗", alternates: "ᨒᨓᨏ" },
    { name: "Steel", symbol: "✧", alternates: "㊎✦⚓" },
    { name: "Water", symbol: "☂", alternates: "㊌≋⛈" },
  ];
  

  async function copyToClipboard(event) {
    try {
      const value = event.target.value; // Access the input value
      await navigator.clipboard.writeText(value);
      alert("Copied to clipboard!");
    } catch (err) {
      console.error("Failed to copy: ", err);
    }
  }

</script>

<main>
  <h1>Pogo Paste</h1>

  <h2>Types</h2>
  <table>
    <tbody>
      {#each types as { name, symbol }}
      <tr>
        <td>{name}</td>
        <td>
          <input type="button" value={symbol} on:click={copyToClipboard} />
        </td>
      </tr>
    {/each}
    </tbody>
  </table>
  
</main>

<style>

</style>
