package org.lazywizard.radar;

class RendererWrapper<T> implements Comparable<RendererWrapper>
{
    private final Class<T> renderClass;
    private final int renderOrder;

    RendererWrapper(Class<T> renderClass, int renderOrder)
    {
        this.renderClass = renderClass;
        this.renderOrder = renderOrder;
    }

    Class<T> getRendererClass()
    {
        return renderClass;
    }

    int getRenderOrder()
    {
        return renderOrder;
    }

    @Override
    public int compareTo(RendererWrapper other)
    {
        return Integer.compare(this.renderOrder, other.renderOrder);
    }
}
