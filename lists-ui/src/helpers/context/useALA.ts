import { useContext } from 'react';
import ALAContext, { ALAContextProps } from './ALAContext';

export const useALA = (): ALAContextProps =>
  useContext(ALAContext) as ALAContextProps;
