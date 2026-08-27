import { memo, useState } from 'react';
import { ActionIcon, Button, Group, TextInput } from '@mantine/core';
import { faMagnifyingGlass, faXmark } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { FormattedMessage, useIntl } from 'react-intl';

interface SearchInputProps {
  hasError: boolean;
  initialValue: string;
  onSearch: (value: string) => void;
}

export const SearchInput = memo(function SearchInput({
  hasError,
  initialValue,
  onSearch,
}: SearchInputProps) {
  const intl = useIntl();
  const [inputSearchValue, setSearchInputValue] = useState(initialValue);

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      onSearch(inputSearchValue);
    }
  };

  return (
    <Group gap={0} wrap='nowrap' style={{ flexGrow: 1 }}>
      <TextInput
        style={{ flex: 1 }}
        styles={{
          input: {
            borderTopRightRadius: 0,
            borderBottomRightRadius: 0,
            borderRight: 'none',
          },
        }}
        disabled={hasError}
        value={inputSearchValue}
        onChange={(event) => setSearchInputValue(event.currentTarget.value)}
        onKeyDown={handleKeyDown}
        placeholder={intl.formatMessage({
          id: 'search.input.placeholder',
          defaultMessage: 'Search within list',
        })}
        aria-label={intl.formatMessage({
          id: 'search.input.label',
          defaultMessage: 'Search within list',
        })}
        leftSection={
          <FontAwesomeIcon icon={faMagnifyingGlass} fontSize={16} stroke='2' />
        }
        rightSection={
          <ActionIcon
            radius='sm'
            variant='transparent'
            size='xs'
            title={intl.formatMessage({
              id: 'search.clear.label',
              defaultMessage: 'Clear search',
            })}
            aria-label={intl.formatMessage({
              id: 'search.clear.label',
              defaultMessage: 'Clear search',
            })}
            disabled={inputSearchValue.length === 0}
            onClick={() => {
              onSearch('');
              setSearchInputValue('');
            }}
            style={{ marginLeft: 5, marginRight: 10 }}
          >
            <FontAwesomeIcon icon={faXmark} fontSize={20} />
          </ActionIcon>
        }
      />
      <Button
        variant='light'
        styles={{
          root: {
            borderTopLeftRadius: 0,
            borderBottomLeftRadius: 0,
            borderColor: 'var(--mantine-color-default-border)',
          },
        }}
        style={{
          '--button-hover': 'var(--mantine-color-rust-filled-hover)',
          '--button-hover-color': 'white',
        }}
        radius='md'
        onClick={(event) => {
          event.preventDefault();
          onSearch(inputSearchValue);
        }}
      >
        <FormattedMessage id='search.button.label' defaultMessage='Search' />
      </Button>
    </Group>
  );
});
