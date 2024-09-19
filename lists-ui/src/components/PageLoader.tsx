import Lottie, { LottieComponentProps } from 'lottie-react';
import { Center } from '@mantine/core';

// Loader animation
import loader from '../static/ala-loader-quick.json';

interface PageLoaderProps
  extends Omit<LottieComponentProps, 'animationData' | 'style'> {
  size?: number;
}

export default function PageLoader({ size, ...rest }: PageLoaderProps) {
  return (
    <Center w='100%' h='100%'>
      <Lottie
        style={{ width: size || 200, height: size || 200 }}
        animationData={loader}
        autoplay
        {...rest}
      />
    </Center>
  );
}
