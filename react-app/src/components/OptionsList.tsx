import { useEffect, useState } from 'react';
import { getOptions } from './AxiosFunctions';
import { IOption } from '../interfaces';

export default function TestComponent() {
  const [options, setOptions] = useState<IOption[]>([])

  // async / await is clunky with useEffect, promise chain is simplier
  useEffect(() => {
    getOptions()
      .then((response) => {
        const APIOptions: IOption[] = response.data
        setOptions(APIOptions);
      })
      .catch(() => {
        alert("Error")
      })
  }, []);

  return (
    <>
      {
        options.map((option, index) =>
          <div key={index}>
            <h2 role='ticker'>{option.ticker}</h2>
            <h2 role='sunday'>{option.sunday}</h2>
          </div>
        )
      }
    </>
  );
}