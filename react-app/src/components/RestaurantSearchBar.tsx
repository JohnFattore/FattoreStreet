import { Form, Col, Row, Alert } from 'react-bootstrap';
import { useForm } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useEffect } from 'react';

interface IFormInput {
    search: string,
}

export default function RestaurantSearchBar({ setSearch }) {

    const schema = yup.object().shape({
        search: yup.string().required(),
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, watch, formState: { errors } } = useForm<IFormInput>({
        resolver: yupResolver(schema),
        defaultValues: {
            search: ""
        }
    })

    const searchValue = watch("search");

    useEffect(() => {
        setSearch(searchValue); // Update state only when searchValue changes
    }, [searchValue]);

    return (
        <>
            <Form>
                <Row>
                    <Col sm={9}>
                        <Form.Control size="lg" {...register("search", {
                            required: true
                        })} placeholder='Restaurant Search'/>
                        {errors.search && <Alert variant='danger'>This field is required</Alert>}
                    </Col>
                </Row>
            </Form>
        </>
    );
}