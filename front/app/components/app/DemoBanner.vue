<script setup lang="ts">
import { Info } from 'lucide-vue-next'
import { isDemo } from '~/demo/demo'

const visible = ref(isDemo())
</script>

<template>
  <div v-if="visible" class="demo-banner" role="note">
    <Info :size="16" aria-hidden="true" />
    <p>
      <strong>Démo en ligne</strong> : données de démonstration enregistrées depuis la vraie plateforme, connecté en
      administrateur. Navigation libre, modifications désactivées.
    </p>
    <button class="btn btn-ghost btn-icon" aria-label="Masquer" @click="visible = false">×</button>
  </div>
</template>

<style scoped>
.demo-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 32px;
  background: #0f766e;
  color: #fff;
  font-size: 13px;
}

.demo-banner p {
  margin: 0;
  flex: 1;
}

.demo-banner .btn {
  color: #fff;
  font-size: 18px;
}
</style>
