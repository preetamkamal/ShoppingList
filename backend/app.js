import { ApolloServer, gql } from 'apollo-server';

(async () => {
    const { default: fetch } = await import('node-fetch');

    // Define your GraphQL schema.
    const typeDefs = gql`
  type Rating {
    rate: Float
    count: Int
  }
  
  type Product {
    id: ID
    title: String
    description: String
    category: String
    price: Float
    image: String
    rating: Rating
  }
  
  type Query {
    products: [Product]
  }
`;

    // Resolvers to fetch data from both APIs and merge them.
    const resolvers = {
        Query: {
            products: async () => {
                try {
                    // Fetch products from Fake Store API.
                    const res1 = await fetch('https://fakestoreapi.com/products');
                    const products1 = await res1.json();

                    // Fetch products from DummyJSON.
                    const res2 = await fetch('https://dummyjson.com/products');
                    const data2 = await res2.json();
                    const products2 = data2.products; // DummyJSON returns a "products" array.

                    // Transform DummyJSON products so that they match the schema of Fake Store API.
                    const transformedProducts2 = products2.map(product => ({
                        id: product.id,
                        title: product.title,
                        description: product.description,
                        category: product.category,
                        price: product.price,
                        image: product.thumbnail, // or choose product.images[0] if available
                        rating: {
                            rate: product.rating,
                            count: product.stock  // using stock as a proxy for count in this example
                        }
                    }));

                    // Merge both arrays of products.
                    return [...products1, ...transformedProducts2];
                } catch (error) {
                    console.error('Error fetching products:', error);
                    return [];
                }
            }
        }
    };

    const server = new ApolloServer({ typeDefs, resolvers });
    server.listen({ port: 4000 }).then(({ url }) => {
        console.log(`GraphQL endpoint ready at ${url}`);
    });
})();
