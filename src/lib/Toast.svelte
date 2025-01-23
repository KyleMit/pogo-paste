<script>
    export let message = ""; // The toast message
    export let type = "info"; // Toast type (info, success, error, etc.)
    export let duration = 3000; // How long the toast stays visible
    let visible = true;
  
    // Hide the toast after the specified duration
    setTimeout(() => {
      visible = false;
    }, duration);
  </script>
  
  <style>
    .toast {
      position: fixed;
      bottom: 1rem;
      right: 1rem;
      padding: 1rem;
      border-radius: 0.5rem;
      box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
      color: white;
      font-weight: bold;
      transition: opacity 0.3s ease-in-out;
    }
  
    .info {
      background-color: #007bff;
    }
  
    .success {
      background-color: #28a745;
    }
  
    .error {
      background-color: #dc3545;
    }
  
    .hidden {
      opacity: 0;
      pointer-events: none;
    }
  </style>
  
  <div class="toast {type} {visible ? '' : 'hidden'}">
    {message}
  </div>
