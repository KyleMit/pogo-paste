<script lang="ts">
  import Toast from './lib/Toast.svelte';

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
  

  let showToast = false;
  let toastMessage = "";
  let toastType = "info"; // Options: "info", "success", "error"

  async function copyToClipboard(event) {
    let success = false;

    try {
      const value = event.target.value;
      await navigator.clipboard.writeText(value);
      success = true;
    } catch (error) {
      console.error("Failed to copy to clipboard", error);
    }

    toastMessage = success ? "Copied to clipboard!" : "Failed to copy to clipboard";
    toastType = success ? "success" : "error";
    showToast = true;

    // Hide toast after a delay
    setTimeout(() => {
      showToast = false;
    }, 3000);
  }

</script>

<main>
  <h1>Pogo Paste</h1>

  <h2>Types</h2>

  {#if showToast}
    <Toast message={toastMessage} type={toastType} />
  {/if}

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
input[type="button"] {
  width: 40px;
  height: 21px;
  background: white;
  border: 1px solid black;
  border-radius: 4px;
  display: flex;
  place-items: center;
}
tr {
  margin-top: 2px;
}
td + td {
  padding-left: 8px;
}
</style>
