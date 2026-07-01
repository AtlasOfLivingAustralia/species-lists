import { Center, Loader } from '@mantine/core';

interface PageLoaderProps {
  size?: number;
}

export default function PageLoader({ size }: PageLoaderProps) {
  return (
    <Center w='100%' h='100%'>
      <Loader size={size || 64} />
    </Center>
  );
}
