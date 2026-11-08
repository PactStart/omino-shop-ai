<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { platformName } from '../labels'

export interface GoodsCardData {
  goodsId: string
  platform?: string
  title?: string
  imageUrl?: string
  price?: string | null
  couponPrice?: string | null
  salesCount?: number | null
  reviewCount?: number | null
  shopName?: string | null
  reason?: string | null
  subtitle?: string | null
}

const props = defineProps<{ goods: GoodsCardData; compact?: boolean }>()
const router = useRouter()
const broken = ref(false)
const finalPrice = computed(() => props.goods.couponPrice || props.goods.price)
const compare = computed(() => (props.goods.couponPrice ? props.goods.price : ''))

function open() {
  if (props.goods.goodsId) router.push(`/goods/${props.goods.goodsId}`)
}
</script>

<template>
  <button type="button" class="card flex w-full gap-3 p-3 text-left" @click="open">
    <span class="grid h-16 w-16 shrink-0 place-items-center overflow-hidden rounded-[14px] bg-[var(--wash)]">
      <img v-if="goods.imageUrl && !broken" :src="goods.imageUrl" alt="" class="h-full w-full object-cover" @error="broken = true" />
      <span v-else class="text-[10px] font-semibold tracking-wide text-[var(--primary)]">OMINO</span>
    </span>
    <span class="min-w-0 flex-1">
      <span v-if="goods.platform" class="badge" :data-platform="goods.platform">{{ platformName(goods.platform) }}</span>
      <span class="mt-1 line-clamp-2 block text-[0.95rem] font-semibold leading-5">{{ goods.title }}</span>
      <span v-if="goods.reason && !compact" class="mt-0.5 line-clamp-1 block text-[0.75rem] text-[var(--muted)]">{{ goods.reason }}</span>
      <span v-else-if="goods.shopName" class="mt-0.5 block text-[0.75rem] text-[var(--muted)]">{{ goods.shopName }}</span>
      <span class="mt-1 flex items-baseline gap-2">
        <span v-if="finalPrice" class="price text-[1.15rem]">¥{{ finalPrice }}</span>
        <span v-if="compare" class="text-[0.75rem] text-[var(--muted)] line-through">¥{{ compare }}</span>
      </span>
    </span>
  </button>
</template>
